package com.example.kit

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.audio.ProceduralDrumGenerator
import com.example.model.PadSlot
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages kit persistence, sample audio storage, and full Kit Export/Import (zip archives).
 */
class KitManager(private val context: Context) {

    private val tag = "KitManager"
    private val samplesDir: File = File(context.filesDir, "samples").apply { mkdirs() }
    private val exportDir: File = File(context.cacheDir, "kits").apply { mkdirs() }
    private val configFile: File = File(context.filesDir, "kit_config.json")

    /**
     * Initializes the default 12 pads. By user instruction, defaults to empty slots.
     */
    fun createEmptyPads(): List<PadSlot> {
        return List(12) { id ->
            PadSlot(
                id = id,
                name = "Slot ${id + 1}",
                samplePath = null,
                sampleFileName = null,
                midiNote = 36 + id, // C1 (36) to B1 (47)
                midiChannel = 0,
                chokeGroup = 0,
                volume = 1.0f,
                playbackRate = 1.0f,
                colorHex = PadSlot.defaultColorForPad(id)
            )
        }
    }

    /**
     * Saves the current 12 pad configurations to kit_config.json.
     */
    fun saveKitConfig(pads: List<PadSlot>) {
        try {
            val jsonArray = JSONArray()
            for (pad in pads) {
                val obj = JSONObject()
                obj.put("id", pad.id)
                obj.put("name", pad.name)
                obj.put("samplePath", pad.samplePath ?: "")
                obj.put("sampleFileName", pad.sampleFileName ?: "")
                obj.put("midiNote", pad.midiNote)
                obj.put("midiChannel", pad.midiChannel)
                obj.put("chokeGroup", pad.chokeGroup)
                obj.put("volume", pad.volume.toDouble())
                obj.put("playbackRate", pad.playbackRate.toDouble())
                obj.put("varispeed", pad.varispeed)
                obj.put("colorHex", pad.colorHex)
                jsonArray.put(obj)
            }
            configFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(tag, "Failed to save kit config", e)
        }
    }

    /**
     * Loads the saved pad configurations. Falls back to empty pads if none found.
     */
    fun loadSavedKit(): List<PadSlot> {
        if (!configFile.exists()) {
            return createEmptyPads()
        }

        try {
            val jsonStr = configFile.readText()
            val jsonArray = JSONArray(jsonStr)
            val pads = mutableListOf<PadSlot>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getInt("id")
                val samplePathStr = obj.optString("samplePath", "")
                val validPath = if (samplePathStr.isNotEmpty() && File(samplePathStr).exists()) samplePathStr else null

                pads.add(
                    PadSlot(
                        id = id,
                        name = obj.optString("name", "Slot ${id + 1}"),
                        samplePath = validPath,
                        sampleFileName = obj.optString("sampleFileName", null),
                        midiNote = obj.optInt("midiNote", 36 + id),
                        midiChannel = obj.optInt("midiChannel", 0),
                        chokeGroup = obj.optInt("chokeGroup", 0),
                        volume = obj.optDouble("volume", 1.0).toFloat(),
                        playbackRate = obj.optDouble("playbackRate", 1.0).toFloat(),
                        varispeed = obj.optBoolean("varispeed", true),
                        colorHex = obj.optLong("colorHex", PadSlot.defaultColorForPad(id))
                    )
                )
            }

            // Ensure exactly 12 pads
            if (pads.size == 12) {
                return pads
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reading saved kit config", e)
        }

        return createEmptyPads()
    }

    /**
     * Copies an audio sample from a content URI into the internal samples directory.
     * Returns a pair of (destination absolute path, original display name).
     */
    fun importAudioFromUri(uri: Uri, padId: Int): Pair<String, String>? {
        return try {
            val displayName = queryFileName(uri) ?: "sample_pad_${padId + 1}.wav"
            val extension = displayName.substringAfterLast(".", "wav")
            val targetFile = File(samplesDir, "pad_${padId}_${System.currentTimeMillis()}.$extension")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                Pair(targetFile.absolutePath, displayName)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to import audio from URI: $uri", e)
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (colIndex != -1) {
                            result = cursor.getString(colIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        if (result == null) {
            result = uri.path?.let { p ->
                val cut = p.lastIndexOf('/')
                if (cut != -1) p.substring(cut + 1) else p
            }
        }
        return result
    }

    /**
     * Exports the current kit: compresses all active samples + kit_config.json into a zip file,
     * and returns a content URI for sharing or saving.
     */
    fun exportKitToZip(pads: List<PadSlot>, kitName: String = "MTS_Kit"): Uri? {
        try {
            val cleanName = kitName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val zipFile = File(exportDir, "${cleanName}_${System.currentTimeMillis()}.zip")

            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    // 1. Write the kit_config.json with relative sample paths
                    val jsonArray = JSONArray()
                    val filesToInclude = mutableListOf<Pair<String, File>>()

                    for (pad in pads) {
                        val obj = JSONObject()
                        obj.put("id", pad.id)
                        obj.put("name", pad.name)
                        obj.put("midiNote", pad.midiNote)
                        obj.put("midiChannel", pad.midiChannel)
                        obj.put("chokeGroup", pad.chokeGroup)
                        obj.put("volume", pad.volume.toDouble())
                        obj.put("playbackRate", pad.playbackRate.toDouble())
                        obj.put("varispeed", pad.varispeed)
                        obj.put("colorHex", pad.colorHex)

                        val samplePath = pad.samplePath
                        if (!samplePath.isNullOrEmpty()) {
                            val audioFile = File(samplePath)
                            if (audioFile.exists()) {
                                val entryFileName = "sample_pad_${pad.id}_${audioFile.name}"
                                obj.put("relativeSampleFile", entryFileName)
                                obj.put("sampleFileName", pad.sampleFileName ?: audioFile.name)
                                filesToInclude.add(Pair(entryFileName, audioFile))
                            }
                        }
                        jsonArray.put(obj)
                    }

                    // Add config entry
                    val configBytes = jsonArray.toString(2).toByteArray(Charsets.UTF_8)
                    zos.putNextEntry(ZipEntry("kit_config.json"))
                    zos.write(configBytes)
                    zos.closeEntry()

                    // Add all audio files
                    val buffer = ByteArray(8192)
                    for ((entryName, file) in filesToInclude) {
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { fis ->
                            var read: Int
                            while (fis.read(buffer).also { read = it } != -1) {
                                zos.write(buffer, 0, read)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }

            val authority = "${context.packageName}.fileprovider"
            return FileProvider.getUriForFile(context, authority, zipFile)
        } catch (e: Exception) {
            Log.e(tag, "Failed to export kit to zip", e)
            return null
        }
    }

    /**
     * Imports a kit from a zip input stream (URI chosen by user).
     * Extracts samples and restores pad configurations.
     */
    fun importKitFromZip(inputStream: InputStream): List<PadSlot>? {
        val unpackDir = File(samplesDir, "kit_import_${System.currentTimeMillis()}").apply { mkdirs() }
        var configJsonString: String? = null

        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(8192)

                while (entry != null) {
                    val entryName = entry.name
                    if (!entry.isDirectory) {
                        if (entryName == "kit_config.json") {
                            configJsonString = zis.bufferedReader(Charsets.UTF_8).readText()
                        } else {
                            val outFile = File(unpackDir, entryName)
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                var read: Int
                                while (zis.read(buffer).also { read = it } != -1) {
                                    fos.write(buffer, 0, read)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (configJsonString == null) {
                Log.e(tag, "No kit_config.json found in zip")
                return null
            }

            val jsonArray = JSONArray(configJsonString)
            val restoredPads = mutableListOf<PadSlot>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getInt("id")
                val relSample = obj.optString("relativeSampleFile", "")
                val extractedFile = if (relSample.isNotEmpty()) File(unpackDir, relSample) else null
                val samplePath = if (extractedFile != null && extractedFile.exists()) extractedFile.absolutePath else null

                restoredPads.add(
                    PadSlot(
                        id = id,
                        name = obj.optString("name", "Slot ${id + 1}"),
                        samplePath = samplePath,
                        sampleFileName = obj.optString("sampleFileName", extractedFile?.name),
                        midiNote = obj.optInt("midiNote", 36 + id),
                        midiChannel = obj.optInt("midiChannel", 0),
                        chokeGroup = obj.optInt("chokeGroup", 0),
                        volume = obj.optDouble("volume", 1.0).toFloat(),
                        playbackRate = obj.optDouble("playbackRate", 1.0).toFloat(),
                        varispeed = obj.optBoolean("varispeed", true),
                        colorHex = obj.optLong("colorHex", PadSlot.defaultColorForPad(id))
                    )
                )
            }

            saveKitConfig(restoredPads)
            return restoredPads
        } catch (e: Exception) {
            Log.e(tag, "Failed to import kit from zip", e)
            return null
        }
    }

    /**
     * Loads a high quality demo kit of punchy synthetic sounds.
     * Choke group 1 is applied to HiHat Closed (id 2) and HiHat Open (id 6) for realistic choke behavior!
     */
    fun createDemoPreset(): List<PadSlot> {
        val demoDir = File(samplesDir, "demo_kit").apply { mkdirs() }
        val drumTypes = listOf(
            ProceduralDrumGenerator.DrumType.KICK_808 to "808 Kick",
            ProceduralDrumGenerator.DrumType.SNARE_CRISP to "Snare Crisp",
            ProceduralDrumGenerator.DrumType.HIHAT_CLOSED to "Closed Hat", // Choke 1
            ProceduralDrumGenerator.DrumType.CLAP to "Hand Clap",

            ProceduralDrumGenerator.DrumType.KICK_PUNCH to "Punch Kick",
            ProceduralDrumGenerator.DrumType.SNARE_FAT to "Fat Snare",
            ProceduralDrumGenerator.DrumType.HIHAT_OPEN to "Open Hat", // Choke 1
            ProceduralDrumGenerator.DrumType.RIMSHOT to "Rimshot",

            ProceduralDrumGenerator.DrumType.TOM_LOW to "Low Tom",
            ProceduralDrumGenerator.DrumType.TOM_MID to "Mid Tom",
            ProceduralDrumGenerator.DrumType.PERC_WOOD to "Woodblock",
            ProceduralDrumGenerator.DrumType.CYMBAL_CRASH to "Crash Cymbal"
        )

        val pads = mutableListOf<PadSlot>()
        for (i in 0 until 12) {
            val (type, label) = drumTypes[i]
            val file = File(demoDir, "${type.name.lowercase()}.wav")
            ProceduralDrumGenerator.generateSample(file, type)

            // Closed Hat (i=2) and Open Hat (i=6) in Choke Group 1
            val choke = when (i) {
                2, 6 -> 1
                else -> 0
            }

            pads.add(
                PadSlot(
                    id = i,
                    name = label,
                    samplePath = file.absolutePath,
                    sampleFileName = file.name,
                    midiNote = 36 + i,
                    midiChannel = 0,
                    chokeGroup = choke,
                    volume = 1.0f,
                    playbackRate = 1.0f,
                    colorHex = PadSlot.defaultColorForPad(i)
                )
            )
        }

        saveKitConfig(pads)
        return pads
    }
}
