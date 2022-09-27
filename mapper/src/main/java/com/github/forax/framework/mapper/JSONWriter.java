package com.github.forax.framework.mapper;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

public final class JSONWriter {

  private interface Generator {
    String generate(JSONWriter writer, Object o);
  }

  private static final ClassValue<List<Generator>> PROPERTIES_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected List<Generator> computeValue(Class<?> type) {
      var bean = Utils.beanInfo(type);
      var properties = bean.getPropertyDescriptors();
      return Arrays.stream(properties)
              .filter(p->!p.getName().equals("class"))
              .<Generator>map((p) -> {
        var key = "\""+ p.getName() +"\": ";
        var getter = p.getReadMethod();
        return (writer, obj) -> key + writer.toJSON(Utils.invokeMethod(obj, getter));
      }).toList();
    }
  };

  private final HashMap<Class<?>, Function<Object,String>> configurations = new HashMap<>();

  public String toJSON(Object o) {
    return switch (o) {
      case null -> "null";
      case Integer i -> ""+i;
      case Double d -> ""+d;
      case String s -> "\""+ s +"\"";
      case Boolean b -> ""+b;
      case Object __ -> {
        var parser = configurations.get(o.getClass());
        if (parser != null) {
          yield parser.apply(o);
        }
        var generators = PROPERTIES_CLASS_VALUE.get(o.getClass());
        yield generators.stream()
                .map(gen -> gen.generate(this, o))
                .collect(joining(", ", "{", "}"));
      }
    };
  }

  public <T> void configure(Class<T> type, Function<T, String> func) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(func);
    if (configurations.putIfAbsent(type, o -> func.apply(type.cast(o))) != null) { // todo use compose or andThen
      throw new IllegalStateException("Duplicate configuration for " + type.getName());
    };
  }
}
