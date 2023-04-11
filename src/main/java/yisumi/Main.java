package yisumi;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.psu.cse.siis.coal.*;
import edu.psu.cse.siis.coal.arguments.Argument;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import yisumi.booster.ApkBooster;
import yisumi.model.ReflectionExchangable;
import yisumi.model.ReflectionProfile;
import yisumi.model.StmtValue;
import yisumi.model.UniqStmt;
import yisumi.typeref.ArrayVarItemTypeRef;

import com.google.gson.Gson;

/**
 * COAL contribution
 * 
 * No parameter exception
 * Providing a parameter for manually specifying a main method
 * Model.toString() exception, some Field is null, finding the reasons
 * 
 * Check sub-class model and override-method
 * 
 * @author li.li
 *
 */
public class Main 
{
	/**
	 * 0. Some inits
	 * 
	 * 1. Retarget Android app to class (with a single main entrance).
	 * 
	 * 2. Launch COAL for reflection string extractions.
	 * 		In this step, we can also put the results into a database for better usage. (heuristic results)
	 *     
	 * 
	 * 3 Revist the Android app to make sure all the involved Class and methods, 
	 *     fields exist in the current classpath, if not, 
	 *     1) try to dynamically load them, or 
	 *     2) create fake one for all of them.
	 *     
	 *     ==> it can also provide heuristic results for human analysis (e.g., how the app code is dynamically loaded)
	 * 
	 * 4. Revisit the Android app for instrumentation.
	 * 	   Even in this step, if some methods, fields or constructors do not exist, 
	 *     a robust implementation should be able to create them on-the-fly.
	 *      
	 * 5. Based on the instrumented results to perform furture static analysis.
	 * 
	 * @param args
	 */

	public static void main(String[] args) 
	{
		long startTime = System.currentTimeMillis();
		System.out.println("==>TIME:" + startTime);

		String apkPath = "/Users/einstein_bohr/AndroidStudioProjects/buttonTest/app/release/app-release.apk";
		String forceAndroidJar = "/Users/einstein_bohr/Library/Android/sdk/platforms/android-28/android.jar";
		GlobalRef.android_jar = "/Users/einstein_bohr/Library/Android/sdk/platforms/";


		if (args.length >= 2)
		{
			apkPath = args[0];
			forceAndroidJar = args[1];
			GlobalRef.android_jar = args[2];
		}
		
		String apkName = apkPath;
		if (apkName.contains("/"))
		{
			apkName = apkName.substring(apkName.lastIndexOf('/')+1);
		}
		
		if (! new File(GlobalRef.WORKSPACE).exists())
		{
			File workspace = new File(GlobalRef.WORKSPACE);
			workspace.mkdirs();
		}
		
		init(apkPath, forceAndroidJar);
		
		long afterDummyMain = System.currentTimeMillis();
		System.out.println("==>TIME:" + afterDummyMain);
		
		reflectionAnalysis();
		toReadableText(apkName);
		toJson(apkName);
		
		long afterRA = System.currentTimeMillis();
		System.out.println("==>TIME:" + afterRA);
		
		booster();
		
		long afterBooster = System.currentTimeMillis();
		System.out.println("==>TIME:" + afterBooster);
		
		System.out.println("====>TIME_TOTAL:" + startTime + "," + afterDummyMain + "," + afterRA + "," + afterBooster);
	}
	
	public static int test()
	{
		return (int) new Object();
	}
	
	public static void init(String apkPath, String forceAndroidJar)
	{
		DroidRAUtils.extractApkInfo(apkPath);	
		GlobalRef.clsPath = forceAndroidJar;
	}
	
	public static void reflectionAnalysis()
	{
		
		String[] args = {
			"-cp", GlobalRef.clsPath,
			"-model", GlobalRef.coalModelPath,
//			"-model", GlobalRef.rfModelPath,
			"-input", GlobalRef.WORKSPACE
		};

		// identify all array, and model it.
		ArrayVarItemTypeRef.setup(GlobalRef.apkPath, GlobalRef.clsPath);
		GlobalRef.arrayTypeRef = ArrayVarItemTypeRef.arrayTypeRef;

		// run reflection detection.
		DroidRAAnalysis<DefaultCommandLineArguments> analysis = new DroidRAAnalysis<>();
		DefaultCommandLineParser parser = new DefaultCommandLineParser();
		DefaultCommandLineArguments commandLineArguments =
		    parser.parseCommandLine(args, DefaultCommandLineArguments.class);
		if (commandLineArguments != null) 
		{
			AndroidMethodReturnValueAnalyses.registerAndroidMethodReturnValueAnalyses("");
			analysis.performAnalysis(commandLineArguments);
		}
		
		GlobalRef.uniqStmtKeyValues = DroidRAResult.toUniqStmtKeyValues(HeuristicUnknownValueInfer.getInstance().infer(DroidRAResult.stmtKeyValues));
		
		ReflectionProfile.fillReflectionProfile(DroidRAResult.stmtKeyValues);
		GlobalRef.rClasses = ReflectionProfile.rClasses;
		ReflectionProfile.dump();
		ReflectionProfile.dump("==>0:");
		System.out.println("====> missing Unit: " + GlobalRef.missingUnit);
	}
	
	public static void booster()
	{
		ApkBooster.apkBooster(GlobalRef.apkPath, GlobalRef.clsPath, GlobalRef.WORKSPACE);
	}
	
	public static void toReadableText(String apkName)
	{
		try 
		{
			PrintStream systemPrintStream = System.out;
					
			PrintStream fileStream = new PrintStream(new File("droidra_" + apkName + "_" + GlobalRef.pkgName + "_v" + GlobalRef.apkVersionCode + ".txt"));
			System.setOut(fileStream);
			
			System.out.println("The following values were found:");
		    for (Result result : DroidRAResultProcessor.results) 
		    {
		    	((DefaultResult) result).dump();
		    }

			List<MethodOrMethodContext> eps =
					new ArrayList<MethodOrMethodContext>(Scene.v().getEntryPoints());
			ReachableMethods reachableMethods =
					new ReachableMethods(Scene.v().getCallGraph(), eps.iterator(), null);
			reachableMethods.update();
			DefaultResult missing_result = new DefaultResult();
			for (SootClass sootClass : Scene.v().getClasses()) {
				try {
					for (SootMethod sootMethod : sootClass.getMethods()) {
						if (!sootMethod.hasActiveBody())
							continue;
						Body body = sootMethod.getActiveBody();
						for (Unit unit : body.getUnits()) {
							if (!reachableMethods.contains(sootMethod)) {
								try {
									Argument[] arguments = Model.v().getArgumentsForQuery((Stmt) unit);
									if (arguments != null) {
										missing_result.addResult(unit, -1, null);
									}
								}
								catch (soot.ResolutionFailedException ignore) {

								}
							}
						}
					}
				}
				catch (java.util.ConcurrentModificationException ignore) {

				}
			}
			missing_result.dump();
			
			System.setOut(systemPrintStream);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}
	
	public static void toJson(String apkName)
	{
		String jsonFilePath = apkName + ".json";

		Gson gson = new Gson();
		
		ReflectionExchangable re = new ReflectionExchangable();
		re.set(GlobalRef.uniqStmtKeyValues);
		
		try 
		{
			FileWriter fileWriter = new FileWriter(jsonFilePath);
			fileWriter.write(gson.toJson(re));
			
			fileWriter.flush();
			fileWriter.close();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	public static void loadJsonBack()
	{
		String jsonFilePath = GlobalRef.jsonFile;
		
		Gson gson = new Gson();
		
		try 
		{
			BufferedReader reader = new BufferedReader(new FileReader(jsonFilePath));
			ReflectionExchangable re = gson.fromJson(reader, ReflectionExchangable.class);
			
			Map<UniqStmt, StmtValue> map = re.get();
           	
           	for (Map.Entry<UniqStmt, StmtValue> entry : map.entrySet())
           	{
           		System.out.println(entry.getKey().className);
           		System.out.println("    " + entry.getValue());
           	}
           	
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
