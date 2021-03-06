package noise;

import data.Vector;
import util.VolumeUtils;

import java.util.Random;

/**
 * second attempt at perlin noise algorithm
 */

public class PerlinNoiseGeneration {
    private int[] permutation;

    private static final int p[] = {151, 160, 137, 91, 90, 15,
            131, 13, 201, 95, 96, 53, 194, 233, 7, 225, 140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23,
            190, 6, 148, 247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32, 57, 177, 33,
            88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175, 74, 165, 71, 134, 139, 48, 27, 166,
            77, 146, 158, 231, 83, 111, 229, 122, 60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244,
            102, 143, 54, 65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169, 200, 196,
            135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64, 52, 217, 226, 250, 124, 123,
            5, 202, 38, 147, 118, 126, 255, 82, 85, 212, 207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42,
            223, 183, 170, 213, 119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9,
            129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104, 218, 246, 97, 228,
            251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241, 81, 51, 145, 235, 249, 14, 239, 107,
            49, 192, 214, 31, 181, 199, 106, 157, 184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254,
            138, 236, 205, 93, 222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180
    };

    /**
     * @param seed seed if desired to generate pseudo-random permutations. -1 means no specified seed
     */
    public PerlinNoiseGeneration(long seed) {
        generateHashTable(seed);
    }

    private void generateHashTable(Long seed) {
        Random r = new Random();
        this.permutation = new int[512];
        if (seed != -1) {
            r.setSeed(seed);
            for (int i = 0; i < permutation.length; i++) {
                this.permutation[i] = r.nextInt(256);
            }
        } else {
            for (int i = 0; i < 256; i++) {
                this.permutation[256 + i] = this.permutation[i] = p[i];
            }
        }
    }

    /**
     * @param fromZeroToOne boolean value if returned value is in bounds [0, 1] or [-1, 1]
     * @return perlin noise value in specified point
     */
    public double perlin(double x, double y, double z, boolean fromZeroToOne) {
        int xi = (int) x & 255;
        int yi = (int) y & 255;
        int zi = (int) z & 255;

        double xf = x - (int) x;
        double yf = y - (int) y;
        double zf = z - (int) z;

        double u = VolumeUtils.fade(xf);
        double v = VolumeUtils.fade(yf);
        double w = VolumeUtils.fade(zf);

        int aaa = permutation[permutation[permutation[xi] + yi] + zi];
        int aba = permutation[permutation[permutation[xi] + inc(yi)] + zi];
        int aab = permutation[permutation[permutation[xi] + yi] + inc(zi)];
        int abb = permutation[permutation[permutation[xi] + inc(yi)] + inc(zi)];
        int baa = permutation[permutation[permutation[inc(xi)] + yi] + zi];
        int bba = permutation[permutation[permutation[inc(xi)] + inc(yi)] + zi];
        int bab = permutation[permutation[permutation[inc(xi)] + yi] + inc(zi)];
        int bbb = permutation[permutation[permutation[inc(xi)] + inc(yi)] + inc(zi)];

        double x1 = lerp(grad(aaa, xf, yf, zf), grad(baa, xf - 1, yf, zf), u);
        double x2 = lerp(grad(aba, xf, yf - 1, zf), grad(bba, xf - 1, yf - 1, zf), u);
        double y1 = lerp(x1, x2, v);

        x1 = lerp(grad(aab, xf, yf, zf - 1), grad(bab, xf - 1, yf, zf - 1), u);
        x2 = lerp(grad(abb, xf, yf - 1, zf - 1), grad(bbb, xf - 1, yf - 1, zf - 1), u);

        double y2 = lerp(x1, x2, v);

        if (fromZeroToOne)
            return (lerp(y1, y2, w) + 1) / 2;
        return lerp(y1, y2, w);
    }

    /**
     * @return perlin noise value in specified point, returns vector
     */
    public Vector perlin(double x, double y, double z) {
        int xi = (int) x & 255;
        int yi = (int) y & 255;
        int zi = (int) z & 255;

        double xf = x - (int) x;
        double yf = y - (int) y;
        double zf = z - (int) z;

        double u = VolumeUtils.fade(xf);
        double v = VolumeUtils.fade(yf);
        double w = VolumeUtils.fade(zf);

        int aaa = permutation[permutation[permutation[xi] + yi] + zi];
        int aba = permutation[permutation[permutation[xi] + inc(yi)] + zi];
        int aab = permutation[permutation[permutation[xi] + yi] + inc(zi)];
        int abb = permutation[permutation[permutation[xi] + inc(yi)] + inc(zi)];
        int baa = permutation[permutation[permutation[inc(xi)] + yi] + zi];
        int bba = permutation[permutation[permutation[inc(xi)] + inc(yi)] + zi];
        int bab = permutation[permutation[permutation[inc(xi)] + yi] + inc(zi)];
        int bbb = permutation[permutation[permutation[inc(xi)] + inc(yi)] + inc(zi)];

        double x1 = lerp(grad(aaa, xf, yf, zf), grad(baa, xf - 1, yf, zf), u);
        double x2 = lerp(grad(aba, xf, yf - 1, zf), grad(bba, xf - 1, yf - 1, zf), u);
        double y1 = lerp(x1, x2, v);

        x1 = lerp(grad(aab, xf, yf, zf - 1), grad(bab, xf - 1, yf, zf - 1), u);
        x2 = lerp(grad(abb, xf, yf - 1, zf - 1), grad(bbb, xf - 1, yf - 1, zf - 1), u);

        return new Vector(x1, x2, y1).normalize();
    }

    private int inc(int a) {
        return a + 1;
    }

    private double grad(int hash, double x, double y, double z) {
        switch (hash & 0xF) {
            case 0x0:
                return x + y;
            case 0x1:
                return -x + y;
            case 0x2:
                return x - y;
            case 0x3:
                return -x - y;
            case 0x4:
                return x + z;
            case 0x5:
                return -x + z;
            case 0x6:
                return x - z;
            case 0x7:
                return -x - z;
            case 0x8:
                return y + z;
            case 0x9:
                return -y + z;
            case 0xA:
                return y - z;
            case 0xB:
                return -y - z;
            case 0xC:
                return y + x;
            case 0xD:
                return -y + z;
            case 0xE:
                return y - x;
            case 0xF:
                return -y - z;
            default:
                return 0;
        }
    }

//    // fade function 6t^5-15t^4+10t^3
//    private double fade(double t) {
//        return t * t * t * (t * (6 * t - 15) + 10);
//    }

    private double lerp(double a, double b, double x) {
        return a + x * (b - a);
    }

}
