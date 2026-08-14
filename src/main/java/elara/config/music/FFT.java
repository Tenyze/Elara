package elara.config.music;

import java.util.Arrays;

public class FFT {
   private static final ThreadLocal<float[]> realBuf = ThreadLocal.withInitial(() -> new float[0]);
   private static final ThreadLocal<float[]> imagBuf = ThreadLocal.withInitial(() -> new float[0]);
   private static final ThreadLocal<float[]> specBuf = ThreadLocal.withInitial(() -> new float[0]);

   public static float[] compute(float[] pcm, int bands) {
      int n = nextPowerOfTwo(pcm.length);
      if (n < 2) {
         n = 2;
      }

      float[] real = ensureCapacity(realBuf, n);
      float[] imag = ensureCapacity(imagBuf, n);
      Arrays.fill(imag, 0, n, 0.0F);
      int copyLen = Math.min(pcm.length, n);
      System.arraycopy(pcm, 0, real, 0, copyLen);

      for (int i = copyLen; i < n; i++) {
         real[i] = 0.0F;
      }

      for (int var21 = 0; var21 < n; var21++) {
         int n2 = var21;
         real[n2] *= 0.5F * (1.0F - (float)Math.cos((Math.PI * 2) * var21 / (n - 1)));
      }

      int j = 0;

      for (int i2 = 1; i2 < n; i2++) {
         int bit;
         for (bit = n >> 1; (j & bit) != 0; bit >>= 1) {
            j ^= bit;
         }

         if (i2 < (j ^= bit)) {
            float t = real[i2];
            real[i2] = real[j];
            real[j] = t;
            t = imag[i2];
            imag[i2] = imag[j];
            imag[j] = t;
         }
      }

      for (int len = 2; len <= n; len <<= 1) {
         float angle = (float) (-Math.PI * 2) / len;
         float wR = (float)Math.cos(angle);
         float wI = (float)Math.sin(angle);

         for (int i3 = 0; i3 < n; i3 += len) {
            float cR = 1.0F;
            float cI = 0.0F;

            for (int k = 0; k < len / 2; k++) {
               float tR = cR * real[i3 + k + len / 2] - cI * imag[i3 + k + len / 2];
               float tI = cR * imag[i3 + k + len / 2] + cI * real[i3 + k + len / 2];
               real[i3 + k + len / 2] = real[i3 + k] - tR;
               imag[i3 + k + len / 2] = imag[i3 + k] - tI;
               int n3 = i3 + k;
               real[n3] += tR;
               int n4 = i3 + k;
               imag[n4] += tI;
               float nR = cR * wR - cI * wI;
               cI = cR * wI + cI * wR;
               cR = nR;
            }
         }
      }

      float[] spectrum = ensureCapacity(specBuf, bands);
      int halfN = n / 2;
      int bandSize = Math.max(1, halfN / bands);

      for (int i4 = 0; i4 < bands; i4++) {
         float max = 0.0F;

         int idx;
         for (int k = 0; k < bandSize && (idx = i4 * bandSize + k) < halfN; k++) {
            float mag = (float)Math.sqrt(real[idx] * real[idx] + imag[idx] * imag[idx]);
            if (mag > max) {
               max = mag;
            }
         }

         spectrum[i4] = (float)(Math.log10(1.0F + max * 50.0F) / 3.0);
      }

      return spectrum;
   }

   private static float[] ensureCapacity(ThreadLocal<float[]> tl, int minSize) {
      float[] arr = tl.get();
      if (arr.length < minSize) {
         arr = new float[minSize];
         tl.set(arr);
      }

      return arr;
   }

   private static int nextPowerOfTwo(int n) {
      int p = 1;

      while (p < n) {
         p <<= 1;
      }

      return p;
   }
}
