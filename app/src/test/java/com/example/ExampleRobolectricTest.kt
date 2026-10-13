package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.PadSlot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Midi-Triggered Sampler", appName)
  }

  @Test
  fun `test kit config json serialization in android environment`() {
    val pads = List(12) { id ->
      PadSlot(
        id = id,
        name = "Pad ${id + 1}",
        midiNote = 36 + id,
        chokeGroup = if (id == 2 || id == 6) 1 else 0,
        volume = 0.9f,
        playbackRate = 1.0f
      )
    }

    val jsonArray = JSONArray()
    for (pad in pads) {
      val obj = JSONObject()
      obj.put("id", pad.id)
      obj.put("name", pad.name)
      obj.put("midiNote", pad.midiNote)
      obj.put("chokeGroup", pad.chokeGroup)
      jsonArray.put(obj)
    }

    val serialized = jsonArray.toString()
    val deserialized = JSONArray(serialized)

    assertEquals(12, deserialized.length())
    assertEquals(36, deserialized.getJSONObject(0).getInt("midiNote"))
    assertEquals(1, deserialized.getJSONObject(2).getInt("chokeGroup"))
    assertEquals(0, deserialized.getJSONObject(0).getInt("chokeGroup"))
  }
}
