package se.fk.rimfrost.framework.regel.oul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

/**
 * Guards the post-processing of the generated models in build.gradle: Kogito persists process
 * variables with Java serialization, and fails on fields declared as Map.
 */
class GeneratedModelSerializableTest
{
   private static final String CLOUD_EVENT = """
         {"specversion":"1.0","id":"8d7f1b0e-0000-0000-0000-000000000001","source":"x","type":"t",
          "kogitoproctype":"BPMN","unknownTop":true,
          "data":{"handlaggningId":"8d7f1b0e-0000-0000-0000-000000000002","extra":{"n":[1,2]}}}
         """;

   @Test
   void generatedClassesAreSerializable()
   {
      assertInstanceOf(Serializable.class, new RegelOulCancelledMessagePayload());
      assertInstanceOf(Serializable.class, new RegelOulCancelledMessagePayloadData());
   }

   @Test
   void additionalPropertiesIsDeclaredAsHashMap() throws Exception
   {
      assertEquals(HashMap.class,
            RegelOulCancelledMessagePayload.class.getDeclaredField("additionalProperties").getType());
      assertEquals(HashMap.class,
            RegelOulCancelledMessagePayloadData.class.getDeclaredField("additionalProperties").getType());
   }

   @Test
   void payloadSurvivesJavaSerializationRoundTrip() throws Exception
   {
      var payload = new ObjectMapper().readValue(CLOUD_EVENT, RegelOulCancelledMessagePayload.class);
      // Set directly rather than via JSON, so the test doesn't need jackson-datatype-jsr310.
      payload.setTime(OffsetDateTime.parse("2026-10-26T10:15:30+01:00"));

      var bytes = new ByteArrayOutputStream();
      try (var out = new ObjectOutputStream(bytes))
      {
         out.writeObject(payload);
      }
      try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         assertEquals(payload, in.readObject());
      }
   }
}
