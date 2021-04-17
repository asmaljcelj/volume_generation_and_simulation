package fluid_generation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;

/**
 * Fluid generation algorithm with different Perlin noise algorithm
 */

public class FluidGeneration {

    public static void main(String[] args) {
        String endFileName = "fluid_simulation_512.raw";

        // size of the cube (N)
        int size = 512;
        // base height of the fluid (in real-world measurements)
        double heightBase = 30.0;
        // range of height (must be equal in curl and density) - in real-world measurements
        double heightDiff = 10.0;
        // density range +- the base value
        double densityRange = 50;
        // density base value
        double densityBase = 100;
        // how much arbitrary step does solver move in a fluid (by value in each step)
        // total length in each dimension = size * dimensionStep
        double dimensionStep = 0.1;
        // diffusion rate
        double diffusion = 0.001;
        // viscosity rate
        double viscosity = 0.01;
        // time between step
        double dt = 0.05;
        // seed to generate height of fluid (must be equal in curl and density)
        long heightSeed = 12345L;
        // density seed
        long densitySeed = 123L;
        // curl seed
        long curlSeed = 1654987L;
        // number of steps to perform the simulation
        int steps = 20;
        // floor height
        double floorHeight = 7.5;
        // density of floor
        double floorDensity = 255.0;
        // cube size on the floor (real world coordinates)
        double floorCubeSize = 20;
        // cube coordinates
        double cubePositionX = 70.0;
        double cubePositionY = 70.0;

        // initialize both generators
        DensityGeneration densityGenerator = new DensityGeneration();
        CurlNoiseGeneration curlNoiseGenerator = new CurlNoiseGeneration();
        FluidSimulation fluidSimulation = new FluidSimulation(size, diffusion, viscosity, dt);

        // calculate densities
        displayMessageWithTimestamp("Calculating density field");
        DensityGeneration.DensityField densityField = densityGenerator.calculateDensityField(size, densityRange, densityBase, densitySeed, heightSeed, heightBase, dimensionStep, heightDiff);
        // add density
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    fluidSimulation.addDensity(k, j, i, densityField.getDensityAtPoint(k, j, i));
                }
            }
        }
        // free up space
        densityField = null;
        System.gc();

        // calculate potential field
        displayMessageWithTimestamp("Calculating potential field");
        CurlNoiseGeneration.PotentialField potentialField = curlNoiseGenerator.calculatePotentialField(size, curlSeed, heightSeed, dimensionStep, heightBase, heightDiff);
        displayMessageWithTimestamp("Starting setting up environment");
        // add speed
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    CurlNoiseGeneration.Vector potentialVector = potentialField.getVectorAtPoint(k, j, i);
                    fluidSimulation.addVelocity(k, j, i, potentialVector.getX(), potentialVector.getY(), potentialVector.getZ());
                }
            }
        }
        // free up space
        potentialField = null;
        System.gc();
        displayMessageWithTimestamp("Finished setting up environment");

        // simulate fluid for n steps
        for (int i = 0; i < steps; i++) {
            displayMessageWithTimestamp("Started simulation of step " + (i + 1));
            fluidSimulation.simulateStep();
        }

        displayMessageWithTimestamp("Finished with fluid simulation");

        displayMessageWithTimestamp("Generate fluid floor");
        fluidSimulation.setFloor(floorHeight / dimensionStep, floorDensity);

        displayMessageWithTimestamp("Generate cube");
        fluidSimulation.addCubeOnFloor(floorCubeSize / dimensionStep, floorHeight / dimensionStep, cubePositionX, cubePositionY, floorDensity);

//        fluidSimulation.writeDensityAndSpeedToConsole();
        fluidSimulation.writeDensitiesToFile(endFileName, floorDensity);

        displayMessageWithTimestamp("Finished writing densities to file. All done!");
    }

    private static void displayMessageWithTimestamp(String message) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        System.out.println(timestamp + ": " + message);
    }

    static class FluidSimulation {
        // size = N
        int n;
        // actualSize = N + 2
        int size;
        double dt;
        double diff;
        double visc;

        double[] s;
        double[] density;

        double[] velocityX;
        double[] velocityY;
        double[] velocityZ;

        double[] oldVelocityX;
        double[] oldVelocityY;
        double[] oldVelocityZ;

        int iter;

        public FluidSimulation(int n, double diffusion, double viscosity, double dt) {
            this.n = n;
            this.size = n + 2;
            this.diff = diffusion;
            this.dt = dt;
            this.visc = viscosity;
            this.iter = 8;

            this.s = initializeArray();
            this.density = initializeArray();
            this.velocityX = initializeArray();
            this.velocityY = initializeArray();
            this.velocityZ = initializeArray();
            this.oldVelocityX = initializeArray();
            this.oldVelocityY = initializeArray();
            this.oldVelocityZ = initializeArray();
        }

        private double[] initializeArray() {
            return new double[this.size * this.size * this.size];
        }

        private int index(int x, int y, int z) {
            return (x + y * this.size + z * this.size * this.size);
        }

        private int cubeIndex(int x, int y, int z) {
            int endX = x - 1;
            int endY = y - 1;
            int endZ = z - 1;
            return (endX + endY * this.n + endZ * this.n * this.n);
        }

        public void addDensity(int x, int y, int z, double amount) {
            // add density according to real cube
            int cubeX = x + 1;
            int cubeY = y + 1;
            int cubeZ = z + 1;
            this.density[index(cubeX, cubeY, cubeZ)] += amount;
        }

        public void addVelocity(int x, int y, int z, double amountX, double amountY, double amountZ) {
            // add density according to real cube
            int cubeX = x + 1;
            int cubeY = y + 1;
            int cubeZ = z + 1;
            this.velocityX[index(cubeX, cubeY, cubeZ)] = amountX;
            this.velocityY[index(cubeX, cubeY, cubeZ)] = amountY;
            this.velocityZ[index(cubeX, cubeY, cubeZ)] = amountZ;
        }

        public void simulateStep() {
            displayMessageWithTimestamp("Simulating step");
            displayMessageWithTimestamp("Start velocity solver - diffusion");
            swapVelocityX();
            diffuse(1, this.velocityX, this.oldVelocityX, this.visc);
            swapVelocityY();
            diffuse(2, this.velocityY, this.oldVelocityY, this.visc);
            swapVelocityZ();
            diffuse(3, this.velocityZ, this.oldVelocityZ, this.visc);
            project(this.velocityX, this.velocityY, this.velocityZ, this.oldVelocityX, this.oldVelocityY);
            swapVelocityX();
            swapVelocityY();
            swapVelocityZ();
            displayMessageWithTimestamp("Velocity solver - advection");
            advect(1, this.velocityX, this.oldVelocityX, this.oldVelocityX, this.oldVelocityY, this.oldVelocityZ);
            advect(2, this.velocityY, this.oldVelocityY, this.oldVelocityX, this.oldVelocityY, this.oldVelocityZ);
            advect(3, this.velocityZ, this.oldVelocityZ, this.oldVelocityX, this.oldVelocityY, this.oldVelocityZ);
            project(this.velocityX, this.velocityY, this.velocityZ, this.oldVelocityX, this.oldVelocityY);

            displayMessageWithTimestamp("Start density solver");
            swapDensity();
            diffuse(0, this.density, this.s, this.diff);
            swapDensity();
            advect(0, this.density, this.s, this.velocityX, this.velocityY, this.velocityZ);
            displayMessageWithTimestamp("Finished simulating step");
        }


        private void diffuse(int b, double[] newValues, double[] oldValues, double diff) {
            double a = this.dt * diff * this.n * this.n;
            linSolve(b, newValues, oldValues, a, 1 + 4 * a);
        }

        private void advect(int b, double[] newValues, double[] oldValues, double[] velocX, double[] velocY, double[] velocZ) {
            int i0, i1, j0, j1, k0, k1;

            double dt0 = dt * this.n;

            double s0, s1, t0, t1, u0, u1;

            for (int k = 1; k <= this.n; k++, k++) {
                for (int j = 1; j <= this.n; j++, j++) {
                    for (int i = 1; i <= this.n; i++, i++) {
                        double x = i - dt0 * velocX[index(i, j, k)];
                        double y = j - dt0 * velocY[index(i, j, k)];
                        double z = k - dt0 * velocZ[index(i, j, k)];

                        if (x < 0.5)
                            x = 0.5;
                        if (x > (this.n + 0.5))
                            x = this.n + 0.5f;
                        i0 = (int) x;
                        i1 = i0 + 1;
                        if (y < 0.5)
                            y = 0.5;
                        if (y > this.n + 0.5)
                            y = this.n + 0.5;
                        j0 = (int) y;
                        j1 = j0 + 1;
                        if (z < 0.5)
                            z = 0.5;
                        if (z > this.n + 0.5)
                            z = this.n + 0.5;
                        k0 = (int) z;
                        k1 = k0 + 1;

                        s1 = x - i0;
                        s0 = 1 - s1;
                        t1 = y - j0;
                        t0 = 1 - t1;
                        u1 = z - k0;
                        u0 = 1 - u1;

                        newValues[index(i, j, k)] =
                                s0 * (t0 * (u0 * oldValues[index(i0, j0, k0)]
                                        + u1 * oldValues[index(i0, j0, k1)])
                                        + (t1 * (u0 * oldValues[index(i0, j1, k0)]
                                        + u1 * oldValues[index(i0, j1, k1)]))) +
                                        s1 * (t0 * (u0 * oldValues[index(i1, j0, k0)]
                                                + u1 * oldValues[index(i1, j0, k1)])
                                                + (t1 * (u0 * oldValues[index(i1, j1, k0)]
                                                + u1 * oldValues[index(i1, j1, k1)])));
                    }
                }
            }
            setBnd(b, newValues);
        }

        private void setBnd(int b, double[] x) {
            for (int j = 1; j <= this.n; j++) {
                for (int i = 1; i <= this.n; i++) {
                    x[index(i, j, 0)] = b == 3 ? -x[index(i, j, 1)] : x[index(i, j, 1)];
                    x[index(i, j, this.n + 1)] = b == 3 ? -x[index(i, j, this.n)] : x[index(i, j, this.n)];
                }
            }
            for (int k = 1; k <= this.n; k++) {
                for (int i = 1; i <= this.n; i++) {
                    x[index(i, 0, k)] = b == 2 ? -x[index(i, 1, k)] : x[index(i, 1, k)];
                    x[index(i, this.n + 1, k)] = b == 2 ? -x[index(i, this.n, k)] : x[index(i, this.n, k)];
                }
            }
            for (int k = 1; k <= this.n; k++) {
                for (int j = 1; j <= this.n; j++) {
                    x[index(0, j, k)] = b == 1 ? -x[index(1, j, k)] : x[index(1, j, k)];
                    x[index(this.n + 1, j, k)] = b == 1 ? -x[index(this.n, j, k)] : x[index(this.n, j, k)];
                }
            }

            x[index(0, 0, 0)] = 0.33f * (x[index(1, 0, 0)] + x[index(0, 1, 0)] + x[index(0, 0, 1)]);
            x[index(0, this.n + 1, 0)] = 0.33f * (x[index(1, this.n + 1, 0)] + x[index(0, this.n, 0)] + x[index(0, this.n + 1, 1)]);
            x[index(0, 0, this.n + 1)] = 0.33f * (x[index(1, 0, this.n + 1)] + x[index(0, 1, this.n + 1)] + x[index(0, 0, this.n)]);
            x[index(0, this.n + 1, this.n + 1)] = 0.33f * (x[index(1, this.n + 1, this.n + 1)] + x[index(0, this.n, this.n + 1)] + x[index(0, this.n + 1, this.n)]);
            x[index(this.n + 1, 0, 0)] = 0.33f * (x[index(this.n, 0, 0)] + x[index(this.n + 1, 1, 0)] + x[index(this.n + 1, 0, 1)]);
            x[index(this.n + 1, this.n + 1, 0)] = 0.33f * (x[index(this.n, this.n + 1, 0)] + x[index(this.n + 1, this.n, 0)] + x[index(this.n + 1, this.n + 1, 1)]);
            x[index(this.n + 1, 0, this.n + 1)] = 0.33f * (x[index(this.n, 0, this.n + 1)] + x[index(this.n + 1, 1, this.n + 1)] + x[index(this.n + 1, 0, this.n)]);
            x[index(this.n + 1, this.n + 1, this.n + 1)] = 0.33f * (x[index(this.n, this.n + 1, this.n + 1)] + x[index(this.n + 1, this.n, this.n + 1)] + x[index(this.n + 1, this.n + 1, this.n)]);
        }

        private void linSolve(int b, double[] newValues, double[] oldValues, double a, double c) {
            for (int k = 0; k < this.iter; k++) {
                for (int m = 1; m <= this.n; m++) {
                    for (int j = 1; j <= this.n; j++) {
                        for (int i = 1; i <= this.n; i++) {
                            newValues[index(i, j, m)] = (oldValues[index(i, j, m)] +
                                    a * (newValues[index(i + 1, j, m)] + newValues[index(i - 1, j, m)] +
                                            newValues[index(i, j - 1, m)] + newValues[index(i, j + 1, m)] +
                                            newValues[index(i, j, m - 1)] + newValues[index(i, j, m + 1)])) / c;
                        }
                    }
                }
                setBnd(b, newValues);
            }
        }

        private void project(double[] velX, double[] velY, double[] velZ, double[] p, double[] div) {
            double h = 1.0 / this.n;

            for (int k = 1; k <= this.n; k++) {
                for (int j = 1; j <= this.n; j++) {
                    for (int i = 1; i <= this.n; i++) {
                        div[index(i, j, k)] = -0.5 * h * (
                                velX[index(i + 1, j, k)]
                                        - velX[index(i - 1, j, k)]
                                        + velY[index(i, j + 1, k)]
                                        - velY[index(i, j - 1, k)]
                                        + velZ[index(i, j, k + 1)]
                                        - velZ[index(i, j, k - 1)]);
                        p[index(i, j, k)] = 0;
                    }
                }
            }
            setBnd(0, div);
            setBnd(0, p);
            linSolve(0, p, div, 1, 4);

            for (int k = 1; k <= this.n; k++) {
                for (int j = 1; j <= this.n; j++) {
                    for (int i = 1; i <= this.n; i++) {
                        velX[index(i, j, k)] -= 0.5 * (p[index(i + 1, j, k)]
                                - p[index(i - 1, j, k)]) / h;
                        velY[index(i, j, k)] -= 0.5 * (p[index(i, j + 1, k)]
                                - p[index(i, j - 1, k)]) / h;
                        velZ[index(i, j, k)] -= 0.5 * (p[index(i, j, k + 1)]
                                - p[index(i, j, k - 1)]) / h;
                    }
                }
            }
            setBnd(1, velX);
            setBnd(2, velY);
            setBnd(3, velZ);
        }

        /**
         * @param floorHeight: height of floor in relative coordinate
         */
        public void setFloor(double floorHeight, double floorDensity) {
            for (int i = 1; i < floorHeight; i++) {
                for (int j = 1; j < this.size; j++) {
                    for (int k = 1; k < this.size; k++) {
                        this.density[index(k, j, i)] = floorDensity;
                    }
                }
            }
        }

        /**
         * puts a cube of floorDensity of cubeHeight on the
         */
        public void addCubeOnFloor(double cubeHeight, double floorHeight, double cubePosX, double cubePosY, double floorDensity) {
            for (int i = (int) floorHeight; i < cubeHeight + floorHeight; i++) {
                for (int j = (int) cubePosY; j < cubePosY + cubeHeight; j++) {
                    for (int k = (int) cubePosX; k < cubePosX + cubeHeight; k++) {
                        this.density[index(k, j, i)] = floorDensity;
                    }
                }
            }
        }

        public void writeDensitiesToFile(String fileName, double floorDensity) {
            int totalSize = this.n * this.n * this.n;
            double[] minMax = getMaxMinDensity(floorDensity);
            byte[] array = new byte[totalSize];
            for (int i = 1; i <= this.n; i++) {
                for (int j = 1; j <= this.n; j++) {
                    for (int k = 1; k <= this.n; k++) {
                        double d = this.density[index(k, j, i)];
                        array[cubeIndex(k, j, i)] = byteMap(minMax[0], minMax[1], d, floorDensity);
                    }
                }
            }
            try {
                FileOutputStream fo = new FileOutputStream(fileName);
                fo.write(array);
                fo.close();
            } catch (IOException e) {
                displayMessageWithTimestamp("Error during writing to file");
            }
        }

        public void writeDensityAndSpeedToConsole() {
            for (int i = 1; i <= this.n; i++) {
                for (int j = 1; j <= this.n; j++) {
                    for (int k = 1; k <= this.n; k++) {
                        double d = this.density[index(k, j, i)];
                        double x = this.velocityX[index(k, j, i)];
                        double y = this.velocityY[index(k, j, i)];
                        double z = this.velocityZ[index(k, j, i)];
                        System.out.printf("d: %.2f x: %.2f y: %.2f z: %.2f |\t", d, x, y, z);
//                        System.out.printf("d: %.2f |\t", d);
                    }
                    System.out.println();
                }
            }
        }

        public double[] getMaxMinDensity(double floorDensity) {
            double[] minMax = new double[2];
            double min = Float.MAX_VALUE;
            double max = Float.MIN_VALUE;
            for (int i = 1; i <= this.n; i++)
                for (int j = 1; j <= this.n; j++)
                    for (int k = 1; k <= this.n; k++) {
                        double density = this.density[index(k, j, i)];
                        if (density >= floorDensity)
                            continue;
                        if (density <= 1)
                            continue;
                        if (density < min)
                            min = density;
                        if (density > max)
                            max = density;
                    }
            minMax[0] = min;
            minMax[1] = max;
            return minMax;
        }

        private byte byteMap(double min, double max, double value, double floorDensity) {
            if (value <= 1)
                return (byte) 0;
            if (value >= floorDensity)
                return (byte) 255;

            double interval = max - min;
            double percentage = (value - min) / interval;
            int mappedValue = (int) (percentage * 255);
            return (byte) mappedValue;
        }

        private void swapDensity() {
            double[] temp = this.density;
            this.density = this.s;
            this.s = temp;
        }

        private void swapVelocityX() {
            double[] temp = this.velocityX;
            this.velocityX = this.oldVelocityX;
            this.oldVelocityX = temp;
        }

        private void swapVelocityY() {
            double[] temp = this.velocityY;
            this.velocityY = this.oldVelocityY;
            this.oldVelocityY = temp;
        }

        private void swapVelocityZ() {
            double[] temp = this.velocityZ;
            this.velocityZ = this.oldVelocityZ;
            this.oldVelocityZ = temp;
        }

    }

}
