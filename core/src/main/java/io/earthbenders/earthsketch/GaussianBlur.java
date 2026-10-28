package io.earthbenders.earthsketch;

public final class GaussianBlur {

    private GaussianBlur() {}

    /**
     * Blurs a 2D float field into a new array using a separable Gaussian kernel.
     * The source array is left untouched
     *
     * @param src    source array, indexed as z * dimX + x
     * @param dimX   number of cells along x
     * @param dimY   number of cells along z
     * @param radius kernel radius in cells; larger = smoother, slower
     * @param sigma  standard deviation of the Gaussian; radius/2 is a good default
     * @return a new array of the same length containing the blurred values
     */
    public static float[] blurCopy(float[] src, int dimX, int dimY,
                                   int radius, float sigma) {
        float[] dst = new float[src.length];
        blur(src, dst, dimX, dimY, radius, sigma);
        return dst;
    }

    /**
     * Blurs a 2D float field using a separable Gaussian kernel.
     * Writes into dst; src is not modified. dst may be the same array as src
     * if you want in-place behaviour (an internal temp array is used either way).
     *
     * @param src    source array, indexed as z * dimX + x
     * @param dst    destination array, same layout
     * @param dimX   number of cells along x
     * @param dimY   number of cells along z
     * @param radius kernel radius in cells
     * @param sigma  standard deviation of the Gaussian
     */
    public static void blur(float[] src, float[] dst, int dimX, int dimY,
                            int radius, float sigma) {

        if (radius < 1) {
            // Nothing to do — copy through.
            System.arraycopy(src, 0, dst, 0, src.length);
            return;
        }

        float[] kernel = buildKernel(radius, sigma);
        float[] tmp = new float[src.length];

        // Pass 1: horizontal blur (src -> tmp)
        for (int z = 0; z < dimY; z++) {
            int rowStart = z * dimX;
            for (int x = 0; x < dimX; x++) {
                float sum = 0f;
                for (int k = -radius; k <= radius; k++) {
                    int sx = clamp(x + k, 0, dimX - 1);
                    sum += src[rowStart + sx] * kernel[k + radius];
                }
                tmp[rowStart + x] = sum;
            }
        }

        // Pass 2: vertical blur (tmp -> dst)
        for (int z = 0; z < dimY; z++) {
            for (int x = 0; x < dimX; x++) {
                float sum = 0f;
                for (int k = -radius; k <= radius; k++) {
                    int sz = clamp(z + k, 0, dimY - 1);
                    sum += tmp[sz * dimX + x] * kernel[k + radius];
                }
                dst[z * dimX + x] = sum;
            }
        }
    }

    /**
     * Builds a normalised 1D Gaussian kernel of length (2 * radius + 1).
     */
    private static float[] buildKernel(int radius, float sigma) {
        int size = radius * 2 + 1;
        float[] kernel = new float[size];

        float twoSigmaSq = 2f * sigma * sigma;
        float sum = 0f;

        for (int i = -radius; i <= radius; i++) {
            float v = (float) Math.exp(-(i * i) / twoSigmaSq);
            kernel[i + radius] = v;
            sum += v;
        }

        // Normalise so the kernel sums to 1 — preserves overall brightness.
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
