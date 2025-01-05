package com.example.mycamera.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

data class Result(
    val classIndex:Int,
    val score:Float,
    val rect: Rect
)

interface OnDetectedListener{
    fun onError(err:String)
    fun onResults(results:List<Result>)
    fun onModelLoaded(names:Map<Int, String>)
}

class PytorchDetector(
    private val context: Context,
    private val modelName:String,
    private val labelName:String,
    private val listener: OnDetectedListener
) {
    private lateinit var module: Module

    // model output is of size 25200*(num_of_class+5)
    private val outputRow = 25200 // as decided by the YOLOv5 model for input image of size 640*640
    private var outputColumn = 5 /// left, top, right, bottom, score + class 수
    private val threshold = 0.7f // score above which a detection is generated
    private val nmsLimit = 3
    private lateinit var bitmap: Bitmap

    var names = mapOf<Int, String>()

    init {
        try {
            module = LiteModuleLoader.load(assetFilePath(modelName))
            loadNames()
            outputColumn = 5 + names.size
            Log.d("Object Detection", "$modelName Loaded, outColumn=$outputColumn")
            listener.onModelLoaded(names)
        } catch (e: IOException) {
            Log.e("Object Detection", "Error loading the model", e)
            // 오류 처리: 적절한 방법으로 오류를 처리하거나 알림
            listener.onError("Error loading the model: ${e.message}")
        }
    }


    fun detect(image: ImageProxy, resultWidth: Int, resultHeight: Int) {
        try {
            if (!::bitmap.isInitialized) {
                bitmap = Bitmap.createBitmap(
                    image.width,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
            }

            image.use { bitmap.copyPixelsFromBuffer(image.planes[0].buffer) }
            val imageRotation = image.imageInfo.rotationDegrees
            val matrix = Matrix()
            // rotate image
            matrix.postRotate(imageRotation.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true);

            // resize image
            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap,
                INPUT_WIDTH,
                INPUT_HEIGHT,
                true
            )

            // detect 함수 내에 있는 부분 수정
            if (!::module.isInitialized) {
                // 모듈이 초기화되지 않았다면 초기화 진행
                module = LiteModuleLoader.load(assetFilePath(modelName))
                loadNames()
                outputColumn = 5 + names.size
                Log.d("Object Detection", "$modelName Loaded, outColumn=$outputColumn")
                listener.onModelLoaded(names)
            }

            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                NO_MEAN_RGB,
                NO_STD_RGB
            )


            val outputTuple: Array<IValue> = module.forward(IValue.from(inputTensor)).toTuple()
            val outputTensor = outputTuple[0].toTensor()
            val outputs = outputTensor.dataAsFloatArray

            val imgScaleX: Float = bitmap.width.toFloat() / INPUT_WIDTH
            val imgScaleY: Float = bitmap.height.toFloat() / INPUT_HEIGHT
            val ivScaleX: Float = resultWidth.toFloat() / bitmap.width
            val ivScaleY: Float = resultHeight.toFloat() / bitmap.height

            val results: MutableList<Result> = outputsToNMSPredictions(
                outputs,
                imgScaleX,
                imgScaleY,
                ivScaleX,
                ivScaleY,
                0f,
                0f
            )

            // 결과 알림
            listener.onResults(results)
        } catch (e: Exception) {
            Log.e("Object Detection", "Error in detect method", e)
            listener.onError("Error in detect method: ${e.message}")
        }
    }


    private fun loadNames(){
        val yaml = Yaml()
        val inputStream = context.assets.open(labelName)
        names = yaml.load(inputStream) as Map<Int, String>
    }

    private fun assetFilePath(fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (file.exists() && file.length() > 0) return file.absolutePath

        val inputStream: InputStream = context.assets.open(fileName)
        val outputStream: OutputStream = FileOutputStream(file)
        return try {
            val buffer = ByteArray(1024)
            var read: Int? = null
            while (inputStream?.read(buffer).also { read = it!! } != -1) {
                read?.let { outputStream.write(buffer, 0, it) }
            }
            outputStream.flush()
            file.absolutePath
        } catch (e: IOException) {
            ""
        }
    }


    private fun IOU(a: Rect, b: Rect):Float{
        val areaA = (a.right-a.left)*(a.bottom-a.top)
        if(areaA <= 0.0) return 0.0f

        val areaB = (b.right-b.left)*(b.bottom-b.top)
        if(areaB <= 0.0) return 0.0f

        val intersectionMinX = a.left.coerceAtLeast(b.left).toFloat()
        val intersectionMinY = a.top.coerceAtLeast(b.top).toFloat()
        val intersectionMaxX = a.right.coerceAtMost(b.right).toFloat()
        val intersectionMaxY = a.bottom.coerceAtMost(b.bottom).toFloat()

        val intersectionArea =
            (intersectionMaxY - intersectionMinY).coerceAtLeast(0f) *
                    (intersectionMaxX - intersectionMinX).coerceAtLeast(0f)

        return intersectionArea / (areaA + areaB - intersectionArea)
    }

    private fun nonMaxSuppression(
        results:MutableList<Result>,
        limit:Int,
        threshold:Float):MutableList<Result>{

        results.sortWith { o1, o2 -> o1.score.compareTo(o2.score) }

        val selected = mutableListOf<Result>()
        val active = BooleanArray(results.size){true}
        var numActive = active.size

        var done = false
        for(i in 0..results.lastIndex){
            if(done) break

            if(active[i]){
                val boxA = results[i]
                selected.add(boxA)
                if(selected.size >= limit) break

                for(j in i+1..results.lastIndex){
                    if(active[j]){
                        val boxB = results[j]
                        if(IOU(boxA.rect, boxB.rect)>threshold){
                            active[j] = false
                            numActive -= 1
                            if(numActive <= 0){
                                done=true
                                break
                            }
                        }
                    }
                }
            }
        }
        return selected
    }

    private fun outputsToNMSPredictions(
        outputs:FloatArray,
        imageScaleX:Float,
        imageScaleY:Float,
        ivScaleX:Float,
        ivScaleY:Float,
        startX:Float,
        startY:Float
    ):MutableList<Result>{
        val results = mutableListOf<Result>()

        for(i in 0 until outputRow){
            if(outputs[i*outputColumn + 4] > threshold){
                val x = outputs[i* outputColumn]
                val y = outputs[i* outputColumn + 1]
                val w = outputs[i* outputColumn + 2]
                val h = outputs[i* outputColumn + 3]

                val left = imageScaleX*(x-w/2)
                val top = imageScaleY*(y-h/2)
                val right = imageScaleX * (x + w/2)
                val bottom = imageScaleY*(y+h/2)
                var max = outputs[i*outputColumn + 5]

                var cls = 0
                for(j in 0 until (outputColumn - 5)){
                    if(outputs[i*outputColumn + 5 + j] > max){
                        max = outputs[i*outputColumn + 5 + j]
                        cls = j
                    }
                }

                val rect = Rect(
                    (startX+ivScaleX*left).toInt(),
                    (startY+top*ivScaleY).toInt(),
                    (startX+ivScaleX*right).toInt(),
                    (startY+ivScaleY*bottom).toInt()
                )
                results.add(
                    Result(
                        cls,
                        outputs[i * outputColumn + 4],
                        rect
                    )
                )
            }
        }

        return nonMaxSuppression(results, nmsLimit, threshold)
    }

    companion object{
        const val INPUT_WIDTH = 640
        const val INPUT_HEIGHT = 640
        val NO_MEAN_RGB = floatArrayOf(0.0f, 0.0f, 0.0f)
        val NO_STD_RGB = floatArrayOf(1.0f, 1.0f, 1.0f)
    }
}