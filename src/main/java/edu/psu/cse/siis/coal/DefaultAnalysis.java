/*
 * Copyright (C) 2015 The Pennsylvania State University and the University of Wisconsin
 * Systems and Internet Infrastructure Security Laboratory
 *
 * Author: Damien Octeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.psu.cse.siis.coal;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;

import yisumi.GlobalRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.cfg.BiDirICFGFactory;
import soot.jimple.infoflow.cfg.DefaultBiDiICFGFactory;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.toolkits.callgraph.CallGraphBuilder;
import soot.jimple.toolkits.pointer.DumbPointerAnalysis;
import soot.options.Options;
import edu.psu.cse.siis.coal.arguments.ArgumentValueManager;
import edu.psu.cse.siis.coal.arguments.MethodReturnValueManager;
import edu.psu.cse.siis.coal.field.transformers.FieldTransformerManager;

import static soot.jimple.infoflow.InfoflowConfiguration.SootIntegrationMode.UseExistingInstance;

/**
 * A default analysis, which performs constant propagation with the default method argument
 * analyses. Supported field operations are: add, remove, clear and replace. Supported method
 * argument types are int, class and string.
 * 
 * @param <A> A type for command line arguments.
 */
public class DefaultAnalysis<A extends CommandLineArguments> extends Analysis<A> {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  protected void registerFieldTransformerFactories(A commandLineArguments) {
    FieldTransformerManager.v().registerDefaultFieldTransformerFactories();
  }

  @Override
  protected void registerArgumentValueAnalyses(A commandLineArguments) {
    ArgumentValueManager.v().registerDefaultArgumentValueAnalyses();
  }

  @Override
  protected void registerMethodReturnValueAnalyses(A commandLineArguments) {
    MethodReturnValueManager.v().registerDefaultMethodReturnValueAnalyses();
  }

//  @Override
//  protected void initializeAnalysis(A commandLineArguments) {
//    addSceneTransformer();
//
//    Options.v().set_no_bodies_for_excluded(true);
//    Options.v().set_allow_phantom_refs(true);
//    Options.v().set_output_format(Options.output_format_none);
//    Options.v().set_whole_program(true);
//    Options.v().set_soot_classpath(
//        commandLineArguments.getInput() + File.pathSeparator + commandLineArguments.getClasspath());
//
//    Options.v().setPhaseOption("cg.spark", "on");
//
//    // do not merge variables (causes problems with PointsToSets)
//    Options.v().setPhaseOption("jb.ulp", "off");
//
//    // Options.v().setPhaseOption("jb.ne", "off");
//    Options.v().setPhaseOption("jb.uce", "remove-unreachable-traps:true");
//
//    Options.v().setPhaseOption("cg", "trim-clinit:false");
//    Options.v().set_prepend_classpath(true);
//
//    Options.v().set_src_prec(Options.src_prec_java);
//
//    for (String analysisClass : AnalysisParameters.v().getAnalysisClasses())
//    {
//    	try
//    	{
//    		SootClass sootClass = Scene.v().loadClassAndSupport(analysisClass);
//    	    Scene.v().forceResolve(analysisClass, SootClass.BODIES);
//    	    sootClass.setApplicationClass();
//    	}
//    	catch (Exception ex)
//    	{
//    		//TODO: need more investigation
//    	}
//    }
//
//    Scene.v().loadNecessaryClasses();
//
//    //Scene.v().setMainClassFromOptions();
//    // need the class have a `main` method.
//    Scene.v().setMainClass(Scene.v().getSootClass("DummyMainClass"));
//    Scene.v().setEntryPoints(EntryPoints.v().application());
//  }

  @Override
  protected void initializeAnalysis(A commandLineArguments) {
    initSoot();
    // FlowDroid
    InfoflowAndroidConfiguration conf = new InfoflowAndroidConfiguration();
    conf.setStopAfterFirstFlow(false);
    conf.getCallbackConfig().setEnableCallbacks(true);
    conf.setEnableExceptionTracking(true);
    conf.getSourceSinkConfig().setLayoutMatchingMode(InfoflowConfiguration.LayoutMatchingMode.NoMatch);
    conf.setFlowSensitiveAliasing(true);
    conf.setMergeDexFiles(true);
    conf.setSootIntegrationMode(UseExistingInstance);   // use existed soot instance.
    conf.setCallgraphAlgorithm(InfoflowConfiguration.CallgraphAlgorithm.CHA);
    conf.getAnalysisFileConfig().setAndroidPlatformDir(GlobalRef.android_jar);
    conf.getAnalysisFileConfig().setTargetAPKFile(GlobalRef.apkPath);
    conf.setWriteOutputFiles(false);

    SetupApplication app = new SetupApplication(conf);
    app.setCallbackFile("res/AndroidCallbacks.txt");
    app.constructCallgraph();
    logger.info("Construct call graph done.");

    BiDirICFGFactory icfgFactory;
    DefaultBiDiICFGFactory factory = new DefaultBiDiICFGFactory();
    factory.setIsAndroid(true);
    icfgFactory = factory;
    app.setIcfgFactory(factory);
    InfoflowCFG cfg = (InfoflowCFG) icfgFactory.buildBiDirICFG(conf.getCallgraphAlgorithm(),
            conf.getEnableExceptionTracking());

    AnalysisParameters.v().setIcfg(cfg);
    PropagationProblem problem = new PropagationProblem(cfg);
    for (SootMethod ep : Scene.v().getEntryPoints()) {
      if (ep.isConcrete()) {
        problem.getInitialSeeds().add(ep.getActiveBody().getUnits().getFirst());
      }
    }

    int iterationCounter = 0;
    PropagationSolver solver = null;

    while (iterationCounter < 15) {
      PropagationTimers.v().misc.start();
      IterationSolver.v().initialize(solver);
      PropagationTimers.v().misc.end();

      PropagationTimers.v().problemGeneration.start();
      solver = new PropagationSolver(problem);
      PropagationTimers.v().problemGeneration.end();

      PropagationTimers.v().ideSolution.start();
      logger.info("Solving propagation problem (iteration " + iterationCounter + ")");
      solver.solve();
      PropagationTimers.v().ideSolution.end();

      PropagationTimers.v().misc.start();
      if (!AnalysisParameters.v().isIterative() || IterationSolver.v().hasFoundFixedPoint()) {
        iterationCounter = 15;
      } else {
        ++iterationCounter;
      }
    }
    PropagationTimers.v().misc.end();

    logger.info("Reached a fixed point");

    DefaultResultBuilder resultBuilder = new DefaultResultBuilder();;
    Results.addResult(resultBuilder.buildResult(solver));
  }

  @Override
  protected void handleFatalAnalysisException(A commandLineArguments,
      FatalAnalysisException exception) {
    logger.error("Fatal error while analyzing application", exception);
  }

  @Override
  protected void processResults(A commandLineArguments) {
    DefaultResultProcessor processor = new DefaultResultProcessor();
    processor.processResult();
  }

  @Override
  protected void finalizeAnalysis(A commandLineArguments) {
  }

  private void addSceneTransformer() {
    ResultBuilder resultBuilder = new DefaultResultBuilder();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    String debugDirPath = System.getProperty("user.home") + File.separator + "debug";
    File debugDir = new File(debugDirPath);
    if (!debugDir.exists()) {
      debugDir.mkdir();
    }

    String fileName = dateFormat.format(new Date()) + ".txt";
    String debugFilename = debugDirPath + File.separator + fileName;

    String pack = AnalysisParameters.v().useShimple() ? "wstp" : "wjtp";
    Transform transform =
        new Transform(pack + ".ifds", new PropagationSceneTransformer(resultBuilder,
            new PropagationSceneTransformerFilePrinter(debugFilename, new SymbolFilter() {
              @Override
              public boolean filterOut(Value symbol) {
                return false;
              }
            })));
    if (PackManager.v().getPack(pack).get(pack + ".ifds") == null) {
      PackManager.v().getPack(pack).add(transform);
    } else {
      Iterator<?> it = PackManager.v().getPack(pack).iterator();
      while (it.hasNext()) {
        Object current = it.next();
        if (current instanceof Transform
            && ((Transform) current).getPhaseName().equals(pack + ".ifds")) {
          it.remove();
          break;
        }

      }
      PackManager.v().getPack(pack).add(transform);
    }
  }

  private void initSoot() {
    G.reset();
    Options.v().set_src_prec(Options.src_prec_apk);
    Options.v().set_full_resolver(true);
    Options.v().set_drop_bodies_after_load(false);
    Options.v().set_no_bodies_for_excluded(true);
    Options.v().set_allow_phantom_refs(true);
    Options.v().ignore_resolution_errors();
    Options.v().set_no_writeout_body_releasing(true);
    Options.v().set_output_dir(GlobalRef.WORKSPACE);
    Options.v().set_output_format(Options.output_format_none);
    Options.v().set_whole_program(true);
    Options.v().set_process_multiple_dex(true);
    Options.v().set_android_jars(GlobalRef.android_jar);
    Options.v().set_process_dir(Collections.singletonList(GlobalRef.apkPath));
    Options.v().set_prepend_classpath(true);
    Options.v().set_keep_line_number(true);
    Options.v().set_app(true);
    Options.v().set_include_all(true);
//    Options.v().set_soot_classpath(GlobalRef.clsPath);

    Scene.v().loadNecessaryClasses();
    Scene.v().loadDynamicClasses();
    logger.info("Load done, running packs...");

    PackManager.v().runPacks();
    logger.info("RunPacks done, running analysis...");
  }
}
