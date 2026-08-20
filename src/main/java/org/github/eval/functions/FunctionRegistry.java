package org.github.eval.functions;

import java.util.Map;
import java.util.TreeMap;
import org.github.eval.EvaluationException;

/** Case-insensitive function lookup. */
public class FunctionRegistry {

  private final Map<String, FunctionIfc> functions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public void register(String name, FunctionIfc function) {
    functions.put(name, function);
  }

  public FunctionIfc getFunction(String name) {
    FunctionIfc function = functions.get(name);
    if (function == null) {
      throw new EvaluationException("Unknown function: " + name);
    }
    return function;
  }

  public static FunctionRegistry defaultRegistry() {
    FunctionRegistry registry = new FunctionRegistry();
    registry.register("AND", new AndFunction());
    registry.register("OR", new OrFunction());
    registry.register("ROUND", new RoundFunction());
    registry.register("MAX", new MaxFunction());
    registry.register("MIN", new MinFunction());
    return registry;
  }
}
