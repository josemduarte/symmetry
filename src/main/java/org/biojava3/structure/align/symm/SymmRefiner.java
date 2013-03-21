package org.biojava3.structure.align.symm;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.biojava.bio.structure.align.StructureAlignmentFactory;
import org.biojava.bio.structure.align.gui.StructureAlignmentDisplay;
import org.biojava.bio.structure.align.gui.jmol.StructureAlignmentJmol;
import org.biojava.bio.structure.align.model.AFPChain;
import org.biojava.bio.structure.align.util.AlignmentTools;
import org.biojava.bio.structure.align.util.AtomCache;
import org.biojava.bio.structure.align.util.RotationAxis;
import org.biojava.bio.structure.jama.Matrix;
import org.biojava.bio.structure.Atom;
import org.biojava.bio.structure.Group;
import org.biojava.bio.structure.ResidueNumber;
import org.biojava.bio.structure.StructureException;
import org.biojava.bio.structure.StructureTools;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;

/**
 * A utility class for refining symmetric alignments
 * @author Spencer Bliven
 *
 */
public class SymmRefiner {
	
	/**
	 * Takes an AFPChain and replaces the optimal alignment based on an alignment map
	 * @param afpChain The alignment to be modified
	 * @param alignment The new alignment, as a Map
	 * @throws StructureException 
	 */
	private static AFPChain replaceOptAln(AFPChain afpChain, Atom[] ca1, Atom[] ca2,
			Map<Integer, Integer> alignment) throws StructureException {
		
		// Determine block lengths
		Integer[] res1 = alignment.keySet().toArray(new Integer[0]);
		Arrays.sort(res1);
		List<Integer> blockLens = new ArrayList<Integer>(2);
		int optLength = 0;
		Integer lastRes = alignment.get(res1[0]);
		int blkLen = lastRes==null?0:1;
		int blkNum = 0;
		for(int i=1;i<res1.length;i++) {
			Integer currRes = alignment.get(res1[i]);
			assert(currRes != null);// could be converted to if statement if assertion doesn't hold; just modify below as well.
			if(lastRes<currRes) {
				blkLen++;
			} else {
				// CP!
				blockLens.add(blkLen);
				optLength+=blkLen;
				blkLen = 1;
			}
			lastRes = currRes;
		}
		blockLens.add(blkLen);
		optLength+=blkLen;
		
		// Create array structure for alignment
		int[][][] optAln = new int[blockLens.size()][][];
		int pos1 = 0; //index into res1
		for(int blk=0;blk<blockLens.size();blk++) {
			optAln[blk] = new int[2][];
			blkLen = blockLens.get(blk);
			optAln[blk][0] = new int[blkLen];
			optAln[blk][1] = new int[blkLen];
			int pos = 0; //index into optAln
			while(pos<blkLen) {
				optAln[blk][0][pos]=res1[pos1];
				Integer currRes = alignment.get(res1[pos1]);
				optAln[blk][1][pos]=currRes;
				pos++;
				pos1++;
			}
		}
		assert(pos1 == optLength);
		
		// Create length array
		int[] optLens = new int[blockLens.size()];
		for(int i=0;i<blockLens.size();i++) {
			optLens[i] = blockLens.get(i);
		}
		
		//set everything
		AFPChain refinedAFP = (AFPChain) afpChain.clone();
		refinedAFP.setOptLength(optLength);
		refinedAFP.setOptLen(optLens);
		refinedAFP.setOptAln(optAln);
		refinedAFP.setBlockNum(blockLens.size());
		
		//TODO recalculate properties: superposition, tm-score, etc
		Atom[] ca2clone = StructureTools.cloneCAArray(ca2); // don't modify ca2 positions
		AlignmentTools.updateSuperposition(refinedAFP, ca1, ca2clone);
		return refinedAFP;
	}
	
	/**
	 * Refines a CE-Symm alignment so that it is perfectly symmetric.
	 * 
	 * The resulting alignment will have a one-to-one correspondance between
	 * aligned residues of each symmetric part.
	 * @param afpChain Input alignment from CE-Symm
	 * @param k Symmetry order. This can be guessed by {@link CeSymm#getSymmetryOrder(AFPChain)}
	 * @return The refined alignment
	 * @throws StructureException
	 */
	public static AFPChain refineSymmetry(AFPChain afpChain, Atom[] ca1, Atom[] ca2, int k) throws StructureException {
		// The current alignment
		Map<Integer, Integer> alignment = AlignmentTools.alignmentAsMap(afpChain);
		
		// Do the alignment
		Map<Integer, Integer> refined = refineSymmetry(alignment, k);
		
		AFPChain refinedAFP = replaceOptAln(afpChain, ca1, ca2, refined);
		return refinedAFP;
	}
	
	/**
	 * Refines a CE-Symm alignment so that it is perfectly symmetric.
	 * 
	 * The resulting alignment will have a one-to-one correspondance between
	 * aligned residues of each symmetric part.
	 * @param alignment The input alignment, as a map. This will be modified.
	 * @param k Symmetry order. This can be guessed by {@link CeSymm#getSymmetryOrder(AFPChain)}
	 * @return A modified map with the refined alignment
	 * @throws StructureException
	 */
	public static Map<Integer, Integer> refineSymmetry(Map<Integer, Integer> alignment,int k) throws StructureException {

		// Store scores
		Map<Integer, Double> scores = null;
		scores = initializeScores(alignment,scores, k);
		
		
		// Store eligible residues
		// Eligible if:
		//  1. score(x)>0
		//  2. f^K-1(x) is defined
		//	3. score(f^K-1(x))>0
		
		List<Integer> eligible = null;
		eligible = initializeEligible(alignment,scores,eligible,k);
		
		/* For future heap implementation
		Comparator<Integer> scoreComparator = new Comparator<Integer>() {
			@Override public int compare(Integer o1, Integer o2) {
				if(scores.containsKey(o1)) {
					if(scores.containsKey(o2)) {
						// If both have defined scores, compare the scores
						return scores.get(o1).compareTo(scores.get(o2));
					} else {
						// o2 has infinite score, so o1 < o2
						return -1;
					}
				} else {
					//o1 has infinite score
					if(scores.containsKey(o2)) {
						// o1 > o2
						return 1;
					} else {
						//both undefined
						return 0;
					}
				}
			}
		};
		PriorityQueue<Integer> heap = new PriorityQueue<Integer>(alignment.size(), scoreComparator);
		*/
		
		while(!eligible.isEmpty()) {
			// Find eligible residue with lowest scores
			Integer bestRes = null;
			double bestResScore = Double.POSITIVE_INFINITY;
			for(Integer res : eligible) {
				Double score = scores.get(res);
				if(score != null && score < bestResScore) {
					bestResScore = score;
					bestRes = res;
				}
			}
			
			// Find f^k-1(bestRes)
			Integer resK1 = bestRes;
			for(int i=0;i<k-1;i++) {
				assert(resK1!=null);
				resK1 = alignment.get(resK1);
			}
			// Modify alignment
			alignment.put(resK1, bestRes);
			
			//TODO remove edges into loops & too-short paths?
			
			// Update scores
			//TODO only update scores that could change
			scores = initializeScores(alignment,scores, k);

			// Update eligible
			//TODO only update residues which could become ineligible
			eligible = initializeEligible(alignment,scores,eligible,k);
		}
		
		// Remove remaining edges
		Iterator<Integer> alignmentIt = alignment.keySet().iterator();
		while(alignmentIt.hasNext()) {
			Integer res = alignmentIt.next();
			Double score = scores.get(res);
			if(score == null || score>0.0) {
				alignmentIt.remove();
			}
		}
		
		
		return alignment;
	}


	/**
	 * Helper method to initialize eligible residues.
	 * 
	 * Eligible if:
	 *  1. score(x)>0
	 *  2. f^K-1(x) is defined
	 *  3. score(f^K-1(x))>0
	 * @param alignment The alignment with respect to which to calculate eligibility
	 * @param scores An up-to-date map from residues to their scores
	 * @param eligible Starting list of eligible residues. If null will be generated.
	 * @param k
	 * @return
	 */
	private static List<Integer> initializeEligible(Map<Integer, Integer> alignment,
			Map<Integer, Double> scores, List<Integer> eligible, int k) {
		// Eligible if:
		//  1. score(x)>0
		//  2. f^K-1(x) is defined
		//	3. score(f^K-1(x))>0
		if(eligible == null) {
			eligible = new LinkedList<Integer>(alignment.keySet());
		}
		
		Map<Integer, Integer> alignK1 = AlignmentTools.applyAlignment(alignment, k-1);
		
		Iterator<Integer> eligibleIt = eligible.iterator();
		while(eligibleIt.hasNext()) {
			Integer res = eligibleIt.next();
			
			//  2. f^K-1(x) is defined
			if(!alignK1.containsKey(res)) {
				eligibleIt.remove();
				continue;
			}
			Integer k1 = alignK1.get(res);
			if(k1 == null) {
				eligibleIt.remove();
				continue;
			}
			
			//  1. score(x)>0
			Double score = scores.get(res);
			if(score == null || score <= 0.0) {
				eligibleIt.remove();
				continue;
			}
			//	3. score(f^K-1(x))>0
			Double scoreK1 = scores.get(k1);
			if(scoreK1 == null || scoreK1 <= 0.0) {
				eligibleIt.remove();
				continue;
			}
		}
		
		
		return eligible;
	}


	/**
	 * Calculates all scores for an alignment
	 * @param alignment
	 * @param scores A mapping from residues to scores, which will be updated or
	 * 	created if null
	 * @return scores
	 */
	private static Map<Integer, Double> initializeScores(Map<Integer, Integer> alignment,
			Map<Integer, Double> scores, int k) {
		if(scores == null) {
			scores = new HashMap<Integer, Double>(alignment.size());
		} else {
			scores.clear();
		}
		Map<Integer,Integer> alignK = AlignmentTools.applyAlignment(alignment, k);
		
		// calculate input range
		int maxPre = Integer.MIN_VALUE;
		int minPre = Integer.MAX_VALUE;
		for(Integer pre : alignment.keySet()) {
			if(pre>maxPre) maxPre = pre;
			if(pre<minPre) minPre = pre;
		}
		
		for(Integer pre : alignment.keySet()) {
			Integer image = alignK.get(pre);			

			// Use the absolute error score, |x - f^k(x)|
			double score = scoreAbsError(pre,image,minPre,maxPre);
			scores.put(pre, score);
		}
		return scores;
	}

	

	/**
	 * Calculate the score for a residue, specifically the Absolute Error
	 * 	score(x) = |x-f^k(x)|
	 * 
	 * Also includes a small bias based on residue number, for uniqueness..
	 * @param pre x
	 * @param image f^k(x)
	 * @param minPre lowest possible residue number
	 * @param maxPre highest possible residue number
	 * @return
	 */
	private static double scoreAbsError(Integer pre, Integer image,int minPre,int maxPre) {
		// Use the absolute error score, |x - f^k(x)|
		double error;
		if(image == null) {
			error = Double.POSITIVE_INFINITY;
		} else {
			error = Math.abs(pre - image);
		}
		
		//TODO favor lower degree-in
		
		// Add fractional portion relative to sequence position, for uniqueness
		if(error > 0)
			error += (double)(pre-minPre)/(1+maxPre-minPre);
		
		return error;
	}

	public static void main(String[] args) {
		try {
			String name;
			
			name = "1itb.A"; // b-trefoil, C3
			//name = "1tim.A"; // tim-barrel, C8
			//name = "d1p9ha_"; // not rotational symmetry
			name = "3HKE.A"; // very questionable alignment
			//name = "d1jlya1"; // C3 with minimum RSSE at C6
			//name = "1YOX(A:95-160)";
			
			AtomCache cache = new AtomCache();
			Atom[] ca1 = cache.getAtoms(name);
			Atom[] ca2 = cache.getAtoms(name);
			
			StructureAlignmentFactory.addAlgorithm(new CeSymm());
			CeSymm ce = (CeSymm) StructureAlignmentFactory.getAlgorithm(CeSymm.algorithmName);

			// CE-Symm alignment
			long startTime = System.currentTimeMillis();
			AFPChain afpChain = ce.align(ca1, ca2);
			long alignTime = System.currentTimeMillis()-startTime;
			
			afpChain.setName1(name);
			afpChain.setName2(name);
			
			System.out.format("Alignment took %dms%n", alignTime);

			startTime = System.currentTimeMillis();
			int symm = CeSymm.getSymmetryOrder(afpChain);
			long orderTime = System.currentTimeMillis()-startTime;
			System.out.println("Symmetry="+symm);
					
			System.out.format("Finding order took %dms%n", orderTime);

			//Output SIF file
//			String path = "/Users/blivens/dev/bourne/symmetry/refinement/";
//			String filename = path+name+".sif";
//			System.out.println("Writing alignment to "+filename);
//			Writer out = new FileWriter(filename);
//			alignmentToSIF(out, afpChain, ca1, ca2, "bb","ur");
			
			//Refine alignment
			startTime = System.currentTimeMillis();
			AFPChain refinedAFP = refineSymmetry(afpChain, ca1, ca2, symm);
			long refineTime = System.currentTimeMillis()-startTime;
			
			refinedAFP.setAlgorithmName(refinedAFP.getAlgorithmName()+"-refine");
			
			//Output refined to SIF
			System.out.format("Refinement took %dms%n", refineTime);
//			alignmentToSIF(out, refinedAFP, ca1, ca2,"bb","rr");
//			out.close();
			
			//display jmol of alignment
			System.out.println("Original rmsd:"+afpChain.getTotalRmsdOpt());
			System.out.println("New rmsd:"+refinedAFP.getTotalRmsdOpt());
			StructureAlignmentJmol unrefined = StructureAlignmentDisplay.display(afpChain, ca1, StructureTools.cloneCAArray(ca2));
			RotationAxis unrefinedAxis = new RotationAxis(afpChain);
			unrefined.evalString(unrefinedAxis.getJmolScript(ca1));
			
			StructureAlignmentJmol refined = StructureAlignmentDisplay.display(refinedAFP, ca1, StructureTools.cloneCAArray(ca2));
			RotationAxis refinedAxis = new RotationAxis(refinedAFP);
			refined.evalString(refinedAxis.getJmolScript(ca1));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	/**
	 * Creates a simple interaction format (SIF) file for an alignment.
	 * 
	 * The SIF file can be read by network software (eg Cytoscape) to analyze
	 * alignments as graphs.
	 * 
	 * This function creates a graph with residues as nodes and two types of edges:
	 *   1. backbone edges, which connect adjacent residues in the aligned protein
	 *   2. alignment edges, which connect aligned residues
	 * @param out Stream to write to
	 * @param afpChain alignment to write
	 * @param ca1 First protein, used to generate node names
	 * @param ca2 Second protein, used to generate node names
	 * @param backboneInteraction Two-letter string used to identify backbone edges
	 * @param alignmentInteraction Two-letter string used to identify alignment edges
	 * @throws IOException 
	 */
	private static void alignmentToSIF(Writer out,
			AFPChain afpChain, Atom[] ca1,Atom[] ca2, String backboneInteraction, String alignmentInteraction) throws IOException {
		//out.write("Res1\tInteraction\tRes2\n");
		String name1 = afpChain.getName1();
		String name2 = afpChain.getName2();
		if(name1==null) name1=""; else name1+=":";
		if(name2==null) name2=""; else name1+=":";
		
		// Print alignment edges
		int nblocks = afpChain.getBlockNum();
		int[] blockLen = afpChain.getOptLen();
		int[][][] optAlign = afpChain.getOptAln();
		for(int b=0;b<nblocks;b++) {
			for(int r=0;r<blockLen[b];r++) {
				int res1 = optAlign[b][0][r];
				int res2 = optAlign[b][1][r];

				ResidueNumber rn1 = ca1[res1].getGroup().getResidueNumber();
				ResidueNumber rn2 = ca2[res2].getGroup().getResidueNumber();

				String node1 = name1+rn1.getChainId()+rn1.toString();
				String node2 = name2+rn2.getChainId()+rn2.toString();

				out.write(String.format("%s\t%s\t%s\n",node1, alignmentInteraction, node2));
			}
		}

		// Print first backbone edges
		ResidueNumber rn = ca1[0].getGroup().getResidueNumber();
		String last = rn.getChainId()+rn.toString();
		for(int i=1;i<ca1.length;i++) {
			rn = ca1[i].getGroup().getResidueNumber();
			String curr = name1+rn.getChainId()+rn.toString();
			out.write(String.format("%s\t%s\t%s\n",last, backboneInteraction, curr));
			last = curr;
		}
		
		// Print second backbone edges, if the proteins differ
		// Do some quick checks for whether the proteins differ
		// (Not perfect, but should detect major differences and CPs.)
		if(!name1.equals(name2) ||
				ca1.length!=ca2.length ||
				(ca1.length>0 && ca1[0].getGroup()!=null && ca2[0].getGroup()!=null &&
						!ca1[0].getGroup().getResidueNumber().equals(ca2[0].getGroup().getResidueNumber()) ) ) {
			rn = ca2[0].getGroup().getResidueNumber();
			last = rn.getChainId()+rn.toString();
			for(int i=1;i<ca2.length;i++) {
				rn = ca2[i].getGroup().getResidueNumber();
				String curr = name2+rn.getChainId()+rn.toString();
				out.write(String.format("%s\t%s\t%s\n",last, backboneInteraction, curr));
				last = curr;
			}
		}

	}
}
