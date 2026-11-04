package se.fk.rimfrost.framework.regel.oul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the post-processing of the generated models in build.gradle: Kogito persists process
 * variables with Java serialization, and fails on fields declared as Map.
 * <p>
 * The models are discovered from the generator's output directory, so a new schema in
 * asyncapi.yaml is covered without changing this test.
 */
class GeneratedModelSerializableTest
{
   private static final String CLOUD_EVENT = """
         {"specversion":"1.0","id":"8d7f1b0e-0000-0000-0000-000000000001","source":"x","type":"t",
          "kogitoproctype":"BPMN","unknownTop":true,
          "data":{"handlaggningId":"8d7f1b0e-0000-0000-0000-000000000002","extra":{"n":[1,2]}}}
         """;

   private static final ObjectMapper MAPPER = new ObjectMapper();

   @ParameterizedTest
   @MethodSource("generatedClasses")
   void isSerializable(Class<?> c)
   {
      assertTrue(Serializable.class.isAssignableFrom(c));
   }

   @ParameterizedTest
   @MethodSource("generatedClasses")
   void additionalPropertiesIsDeclaredAsHashMap(Class<?> c) throws Exception
   {
      assertEquals(HashMap.class, c.getDeclaredField("additionalProperties").getType());
   }

   @ParameterizedTest
   @MethodSource("generatedClasses")
   void additionalPropertiesSurviveJavaSerializationRoundTrip(Class<?> c) throws Exception
   {
      // Built by reflection, not Jackson: models in a Modelina union (@JsonTypeInfo DEDUCTION)
      // can't be deserialized from an arbitrary JSON object.
      var model = c.getDeclaredConstructor().newInstance();
      var extra = new HashMap<String, Object>();
      extra.put("n", new ArrayList<>(List.of(1, 2)));
      var additional = new HashMap<String, Object>();
      additional.put("extra", extra);
      c.getMethod("setAdditionalProperties", HashMap.class).invoke(model, additional);
      assertRoundTrip(model);
   }

   @Test
   void payloadSurvivesJavaSerializationRoundTrip() throws Exception
   {
      var payload = MAPPER.readValue(CLOUD_EVENT, RegelOulCancelledMessagePayload.class);
      // Set directly rather than via JSON, so the test doesn't need jackson-datatype-jsr310.
      payload.setTime(OffsetDateTime.parse("2026-10-26T10:15:30+01:00"));
      assertRoundTrip(payload);
   }

   private static void assertRoundTrip(Object o) throws Exception
   {
      var bytes = new ByteArrayOutputStream();
      try (var out = new ObjectOutputStream(bytes))
      {
         out.writeObject(o);
      }
      try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         assertEquals(o, in.readObject());
      }
   }

   /**
    * The classes the generator wrote, named from their path under the source root (as javac does), so
    * this doesn't depend on the test's own package. Enums are left out (Serializable already, not
    * patched), and so are the marker interfaces Modelina writes for a channel with several messages.
    */
   static List<Class<?>> generatedClasses() throws Exception
   {
      var dir = System.getProperty("asyncapi.generated.source.dir");
      assertNotNull(dir, "System property asyncapi.generated.source.dir is not set (configured in build.gradle)");

      var root = Path.of(dir);
      List<String> names;
      try (var files = Files.walk(root))
      {
         names = files.map(f -> root.relativize(f).toString())
               .filter(p -> p.endsWith(".java"))
               .map(p -> p.substring(0, p.length() - ".java".length()).replace(File.separatorChar, '.'))
               .sorted()
               .toList();
      }
      var classes = new ArrayList<Class<?>>();
      for (var name : names)
      {
         var c = Class.forName(name);
         if (!c.isEnum() && !c.isInterface())
         {
            classes.add(c);
         }
      }
      assertFalse(classes.isEmpty(), "No generated classes found in " + dir);
      return classes;
   }
}
