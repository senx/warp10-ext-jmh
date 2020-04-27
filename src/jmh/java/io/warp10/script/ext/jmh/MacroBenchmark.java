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
import io.warp10.continuum.Configuration;
import io.warp10.script.MemoryWarpScriptStack;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptLib;
import io.warp10.script.WarpScriptStack;
import io.warp10.warp.sdk.AbstractWarp10Plugin;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class MacroBenchmark {

  @State(Scope.Benchmark)
  public static class MBState {

    MemoryWarpScriptStack stack;
    WarpScriptStack.Macro macro;

    @Setup
    public void doSetup(BenchmarkParams params) throws WarpScriptException, IOException {
      // Set time unit in config if this is a fork
      if (params.getForks() > 0) {
        if (null == System.getProperty(Configuration.WARP_TIME_UNITS)) {
          System.setProperty(Configuration.WARP_TIME_UNITS, "us");
        }
        WarpConfig.setProperties((String) null);

        WarpScriptLib.registerExtensions();
      }

      stack = new MemoryWarpScriptStack(AbstractWarp10Plugin.getExposedStoreClient(), AbstractWarp10Plugin.getExposedDirectoryClient(), WarpConfig.getProperties());
      stack.maxLimits();
      // Authenticate stack
      stack.setAttribute(WarpScriptStack.ATTRIBUTE_TOKEN, "dummytoken");

      // Check the JVM arguments for the macro snapshot file path. We don't use System properties because if there is no fork,
      // they don't have the value.
      for (String jvmArg: params.getJvmArgs()) {
        if (jvmArg.startsWith(JMH.JVM_ARG_PREFIX)) {
          String filename = jvmArg.substring(JMH.JVM_ARG_PREFIX.length());
          List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
          String macroSnapshot = String.join(System.lineSeparator(), lines);
          stack.exec(macroSnapshot);
          macro = (WarpScriptStack.Macro) stack.pop();
          break;
        }
      }
    }
  }

  @Benchmark
  public void benchmarkMacro(MBState mbState) throws WarpScriptException {
    mbState.stack.exec(mbState.macro);
    mbState.stack.clear(); // Extremely fast
  }

}
