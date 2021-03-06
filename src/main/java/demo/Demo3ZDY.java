/**
 *                    BioJava development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the individual
 * authors.  These should be listed in @author doc comments.
 *
 * For more information on the BioJava project and its aims,
 * or to join the biojava-l mailing list, visit the home page
 * at:
 *
 *      http://www.biojava.org/
 *
 * Created on Nov 27, 2012
 * Created by Andreas Prlic
 *
 * @since 3.0.2
 */
package demo;


import java.util.List;

import org.biojava.nbio.structure.Structure;
import org.biojava.nbio.structure.align.gui.jmol.StructureAlignmentJmol;
import org.biojava.nbio.structure.align.util.AtomCache;
import org.biojava.nbio.structure.io.FileParsingParameters;
import org.biojava.nbio.structure.symmetry.analysis.CalcBioAssemblySymmetry;
import org.biojava.nbio.structure.symmetry.core.QuatSymmetryDetector;
import org.biojava.nbio.structure.symmetry.core.QuatSymmetryParameters;
import org.biojava.nbio.structure.symmetry.core.QuatSymmetryResults;
import org.biojava.nbio.structure.symmetry.core.RotationAxisAligner;
import org.biojava.nbio.structure.symmetry.jmolScript.JmolSymmetryScriptGenerator;
import org.biojava.nbio.structure.symmetry.jmolScript.JmolSymmetryScriptGeneratorPointGroup;
import org.biojava.nbio.structure.StructureIO;

public class Demo3ZDY {

	public static void main(String[] args){


		String[] pdbIDs = new String[]{"3ZDY",};

		for ( String pdbID : pdbIDs)
		{
			runPDB(pdbID);
		}

	}

	public static void runPDB(String pdbID){

		pdbID = pdbID.toLowerCase();

		int  biolAssemblyNr = 2;

		Structure s;

		try {

			//			
			AtomCache cache = new AtomCache();
			FileParsingParameters params = cache.getFileParsingParams();
			params.setAlignSeqRes(true);

			params.setParseCAOnly(true);

			params.setLoadChemCompInfo(true);

			StructureIO.setAtomCache(cache);

			s = StructureIO.getBiologicalAssembly(pdbID, biolAssemblyNr);

			String script = "set defaultStructureDSSP true; set measurementUnits ANGSTROMS;  select all;  spacefill off; wireframe off; " +
					"backbone off; cartoon on; color cartoon structure; color structure;  select ligand;wireframe 0.16;spacefill 0.5; " +
					"color cpk ; select all; model 0;set antialiasDisplay true; autobond=false;save STATE state_1;" ;

			String jmolScript = "";			



			CalcBioAssemblySymmetry calc= analyzeSymmetry(s,pdbID, 0,true);

			QuatSymmetryDetector detector = calc.orient();


			boolean hasProtein = detector.hasProteinSubunits();

			if ( ! hasProtein){
				System.err.println("Provided PDB entry has no protein content!");
				return;
			}

			//double sequenceIdThreshold = calc.getParameters().getSequenceIdentityThreshold();

			if ( hasProtein ){
				if  ( detector.getLocalSymmetries().size() > 0 ){

					System.out.println(" Local pseudosymmetry, structure only!");

					jmolScript += getLocalSymmetryScript(detector,calc.getParameters());

				}

				System.out.println(jmolScript);
				
				StructureAlignmentJmol jmol = new StructureAlignmentJmol();
				jmol.setStructure(s);

				String title = "Symmetry results for " + pdbID + " bio assembly: " + biolAssemblyNr ;
				jmol.setTitle(title);
				jmol.evalString(script);
				jmol.evalString(jmolScript);


			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}

	private static CalcBioAssemblySymmetry  analyzeSymmetry(
			Structure s,
			String pdbID, 
			double threshold,
			boolean structureOnly) 
	{

		QuatSymmetryParameters parameters = new QuatSymmetryParameters();

		parameters.setVerbose(true);


		CalcBioAssemblySymmetry calc = new CalcBioAssemblySymmetry(s, parameters);

		return calc;
	}

	private static String getGlobalPseudoSymmetry(
			QuatSymmetryDetector detector, QuatSymmetryParameters parameters) {

		String script = "";
		List<List<QuatSymmetryResults>> globalSymmetries = detector.getLocalSymmetries();
		if ( globalSymmetries == null)
			return "";

		for (List<QuatSymmetryResults> globalSymmetryL : globalSymmetries) {

			for (QuatSymmetryResults globalSymmetry : globalSymmetryL){
				if (parameters.isVerbose()) {
					System.out.println();
					//System.out.println("Results for " + Math.round(parameters.getSequenceIdentityThreshold()*100) + "% sequence identity threshold:");
					System.out.println();
					System.out.println("Global symmetry:");
					System.out.println("Stoichiometry       : " + globalSymmetry.getSubunits().getStoichiometry());
					System.out.println("Pseudostoichiometry : " + globalSymmetry.getSubunits().isPseudoStoichiometric());
					System.out.println("Point group         : " + globalSymmetry.getRotationGroup().getPointGroup());				
					System.out.println("Symmetry RMSD       : " + (float) globalSymmetry.getScores().getRmsd());
				}

				RotationAxisAligner axisTransformation = new RotationAxisAligner(globalSymmetry);

				// use factory method to get point group specific instance of script generator
				JmolSymmetryScriptGenerator scriptGenerator = JmolSymmetryScriptGeneratorPointGroup.getInstance(axisTransformation, "g");

				script += scriptGenerator.getOrientationWithZoom(0);
				script += scriptGenerator.drawPolyhedron();
				script += scriptGenerator.drawAxes();
				script += scriptGenerator.colorBySymmetry();
			}
		}
		script += "draw axes* on; draw poly* on;";

		return script;
	}

	private static String getLocalSymmetryScript(QuatSymmetryDetector detector,QuatSymmetryParameters parameters) {
		String script = "";

		int count = 0;
		for (List<QuatSymmetryResults> localSymmetries: detector.getLocalSymmetries()) {
			System.out.println("result nr XXX ");
			for ( QuatSymmetryResults localSymmetry : localSymmetries) {
				RotationAxisAligner at = new RotationAxisAligner(localSymmetry);
				System.out.println();
				//System.out.println("Results for " + Math.round(parameters.getSequenceIdentityThreshold()*100) + "% sequence identity threshold:");
				System.out.println("Stoichiometry       : " + localSymmetry.getSubunits().getStoichiometry());
				System.out.println("Pseudostoichiometry : " + localSymmetry.getSubunits().isPseudoStoichiometric());
				System.out.println("Point group         : " + localSymmetry.getRotationGroup().getPointGroup());				
				System.out.println("Symmetry RMSD       : " + (float) localSymmetry.getScores().getRmsd());
				System.out.println();
				JmolSymmetryScriptGenerator gen = JmolSymmetryScriptGeneratorPointGroup.getInstance(at, "l"+count);
				if (count == 0) {
					script +=  gen.getOrientationWithZoom(0);

				} 
				script +=  gen.drawPolyhedron();
				script += gen.drawAxes();
				script +=  gen.colorBySymmetry();

				count++;
			}
		}
		script += "draw poly* on; draw axes* on;";

		return script;
	}
}
