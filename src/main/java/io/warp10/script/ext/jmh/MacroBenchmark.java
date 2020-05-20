//
//    Copyright 2020  SenX S.A.S.
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//


package io.warp10.script.ext.jmh;

import io.warp10.WarpConfig;
import io.warp10.script.MemoryWarpScriptStack;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptLib;
import io.warp10.script.WarpScriptStack;
import io.warp10.warp.sdk.AbstractWarp10Plugin;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class MacroBenchmark {

  @State(Scope.Thread)
  public static class MBState {

    MemoryWarpScriptStack stack;
    WarpScriptStack.Macro macro;
    WarpScriptStack.Macro preTrial;
    WarpScriptStack.Macro preIteration;
    WarpScriptStack.Macro preInvocation;
    WarpScriptStack.Macro postInvocation;
    WarpScriptStack.Macro postIteration;
    WarpScriptStack.Macro postTrial;

    @Setup(Level.Trial)
    public void doTrialSetup(BenchmarkParams params) throws WarpScriptException, IOException {
      // Set config if this is a fork
      if (params.getForks() > 0) {
        for (String jvmArg: params.getJvmArgs()) {
          if (jvmArg.startsWith(JMH.JVM_ARG_PREFIX_WARPCONF)) {
            String filename = jvmArg.substring(JMH.JVM_ARG_PREFIX_WARPCONF.length());
            WarpConfig.setProperties(filename);
          }
        }

        WarpScriptLib.registerExtensions();
      }

      stack = new MemoryWarpScriptStack(AbstractWarp10Plugin.getExposedStoreClient(), AbstractWarp10Plugin.getExposedDirectoryClient(), WarpConfig.getProperties());
      stack.maxLimits();
      // Authenticate stack
      stack.setAttribute(WarpScriptStack.ATTRIBUTE_TOKEN, "dummytoken");

      // Check the JVM arguments for the macro snapshot file path. We don't use System properties because if there is no fork,
      // they don't have the value.
      for (String jvmArg: params.getJvmArgs()) {
        if (jvmArg.startsWith(JMH.JVM_ARG_PREFIX_BENCHCONF)) {
          String filename = jvmArg.substring(JMH.JVM_ARG_PREFIX_BENCHCONF.length());
          List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
          String benchConfSnapshot = String.join(System.lineSeparator(), lines);

          stack.exec(benchConfSnapshot);
          Map benchConf = (Map) stack.pop();

          macro = (WarpScriptStack.Macro) benchConf.get(JMH.MACRO_KEY);
          preTrial = (WarpScriptStack.Macro) benchConf.getOrDefault(JMH.PRETRIAL_MACRO, new WarpScriptStack.Macro());
          preIteration = (WarpScriptStack.Macro) benchConf.getOrDefault(JMH.PREITERATION_MACRO, new WarpScriptStack.Macro());
          preInvocation = (WarpScriptStack.Macro) benchConf.getOrDefault(JMH.PREINVOCATION_MACRO, new WarpScriptStack.Macro());
          postInvocation = (WarpScriptStack.Macro) benchConf.getOrDefault(JMH.POSTINVOCATION_MACRO, new WarpScriptStack.Macro());
          postIteration = (WarpScriptStack.Macro) benchConf.getOrDefault(JMH.POSTITERATION_MACRO, new WarpScriptStack.Macro());
          postTrial = (WarpScriptStack.Macro) benchConf.getOrDefault(JMH.POSTTRIAL_MACRO, new WarpScriptStack.Macro());
          break;
        }
      }

      stack.exec(preTrial);
    }

    @Setup(Level.Iteration)
    public void doIterationSetup() throws WarpScriptException {
      stack.exec(preIteration);
    }

    @Setup(Level.Invocation)
    public void doInvocationSetup() throws WarpScriptException {
      stack.exec(preInvocation);
    }

    @TearDown(Level.Invocation)
    public void doInvocationTearDown() throws WarpScriptException {
      stack.exec(postInvocation);
    }

    @TearDown(Level.Iteration)
    public void doIterationTearDown() throws WarpScriptException {
      stack.exec(postIteration);
    }

    @TearDown(Level.Trial)
    public void doTrialTearDown() throws WarpScriptException {
      stack.exec(postTrial);
    }
  }

  @Benchmark
  public void benchmarkMacro(MBState mbState) throws WarpScriptException {
    mbState.stack.exec(mbState.macro);
  }

}
