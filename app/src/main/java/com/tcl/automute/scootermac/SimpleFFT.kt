package com.tcl.automute.scootermac

import kotlin.math.*

class SimpleFFT(val n: Int) {
    init {
        require(n > 0 && n and (n - 1) == 0) { "n must be power of two" }
    }

    fun magnitudeSpectrum(input: DoubleArray): DoubleArray {
        val real = input.copyOf()
        val imag = DoubleArray(n)
        fft(real, imag)
        val half = n / 2
        val mag = DoubleArray(half)
        for (i in 0 until half) mag[i] = sqrt(real[i]*real[i] + imag[i]*imag[i])
        return mag
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wlenReal = cos(ang)
            val wlenImag = sin(ang)
            var i = 0
            while (i < n) {
                var uReal = 1.0
                var uImag = 0.0
                for (j2 in 0 until len/2) {
                    val vReal = real[i + j2 + len/2] * uReal - imag[i + j2 + len/2] * uImag
                    val vImag = real[i + j2 + len/2] * uImag + imag[i + j2 + len/2] * uReal
                    val tReal = real[i + j2]
                    val tImag = imag[i + j2]
                    real[i + j2] = tReal + vReal
                    imag[i + j2] = tImag + vImag
                    real[i + j2 + len/2] = tReal - vReal
                    imag[i + j2 + len/2] = tImag - vImag

                    val tmpReal = uReal * wlenReal - uImag * wlenImag
                    uImag = uReal * wlenImag + uImag * wlenReal
                    uReal = tmpReal
                }
                i += len
            }
            len = len shl 1
        }
    }
}
