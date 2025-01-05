package com.example.mycamera.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageView
import com.example.mycamera.detector.Result

class DetectResultView(context: Context, attrs: AttributeSet?, defStyleAttr:Int) : AppCompatImageView(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet):this(context, attrs, 0)
    constructor(context: Context):this(context, null, 0)

    private var detectionResult:List<Result>? = null
    private var names = mapOf<Int, String>()
    private var paint = Paint()
    private var textPaint = Paint()

    init {
        paint.strokeWidth = 5f
        paint.style = Paint.Style.STROKE
        paint.color = Color.GREEN

        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 60f

    }

    fun setNames(names:Map<Int, String>){
        this.names = names
    }

    fun updateResults(detectionResult: List<Result>?){
        this.detectionResult = detectionResult
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val offset = (height-width)/2

        detectionResult?.forEach {result ->
            Log.d("Canvas", "${names[result.classIndex]}, l:${result.rect.left} r:${result.rect.right}")
            canvas.drawRect(result.rect, paint)
            canvas.drawText(
                String.format(
                    "%s %.2f",
                    names[result.classIndex],
                    result.score),
                result.rect.left + 20f,
                result.rect.top + 70f,
                textPaint
            )
        }
    }
}