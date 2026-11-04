import java.util.Locale;

/**
 * Figure 2.1: Create -> Process 1 -> Process 2 -> Process 3 -> Dispose.
 *
 * Model verification: each stage changes one input parameter, keeps all others
 * at their base values, runs the model several times for every value and prints
 * the averaged results as a table.
 */
public class Main {
    // Base values of the model inputs. Change them here to move the base point.
    static double createDelay = 2.0;
    static double delay1 = 1.0;
    static double delay2 = 1.0;
    static double delay3 = 1.0;
    static int maxQueue1 = 5;
    static int maxQueue2 = 5;
    static int maxQueue3 = 5;
    static double simulationTime = 1000.0;

    // Number of runs per parameter value (at least 4); the results are averaged over them.
    static int runs = 5;

    // Values tried on each verification stage (one parameter changes per stage).
    static double[] createDelayValues = {0.5, 1.0, 2.0, 4.0, 8.0};
    static double[] delayValues = {0.25, 0.5, 1.0, 2.0, 4.0};
    static int[] maxQueueValues = {0, 1, 2, 5, 10};
    static double[] simulationTimeValues = {100.0, 500.0, 1000.0, 5000.0, 10000.0};

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        System.out.println("# Model verification");
        System.out.printf("%nBase values: createDelay = %.2f, delay1 = %.2f, delay2 = %.2f, delay3 = %.2f, "
                        + "maxQueue1 = %d, maxQueue2 = %d, maxQueue3 = %d, simulationTime = %.0f.%n",
                createDelay, delay1, delay2, delay3, maxQueue1, maxQueue2, maxQueue3, simulationTime);
        System.out.println("Every row is the mean of " + runs + " runs.");

        stage("1. Mean interval between arrivals (createDelay)", "createDelay",
                createDelayValues, value -> createDelay = value);
        stage("2. Mean service time of Process 1 (delay1)", "delay1",
                delayValues, value -> delay1 = value);
        stage("3. Mean service time of Process 2 (delay2)", "delay2",
                delayValues, value -> delay2 = value);
        stage("4. Mean service time of Process 3 (delay3)", "delay3",
                delayValues, value -> delay3 = value);
        stage("5. Queue capacity of Process 1 (maxQueue1)", "maxQueue1",
                toDouble(maxQueueValues), value -> maxQueue1 = (int) value);
        stage("6. Queue capacity of Process 2 (maxQueue2)", "maxQueue2",
                toDouble(maxQueueValues), value -> maxQueue2 = (int) value);
        stage("7. Queue capacity of Process 3 (maxQueue3)", "maxQueue3",
                toDouble(maxQueueValues), value -> maxQueue3 = (int) value);
        stage("8. Simulation time (simulationTime)", "simulationTime",
                simulationTimeValues, value -> simulationTime = value);
    }

    /**
     * Runs one verification stage: sets each value of one parameter,
     * averages the runs and restores the base value afterwards.
     */
    private static void stage(String title, String name, double[] values, Setter setter) {
        double[] base = saveParameters();

        System.out.println("\n## " + title + "\n");
        System.out.println("| " + name + " | Created | Disposed | Rejected 1 | Rejected 2 | Rejected 3 "
                + "| Rejection prob. | Load 1 | Load 2 | Load 3 | Mean queue 1 | Mean queue 2 | Mean queue 3 |");
        System.out.println("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");

        for (double value : values) {
            setter.set(value);
            double[] mean = new double[Result.SIZE];
            for (int run = 0; run < runs; run++) {
                double[] result = runModel();
                for (int i = 0; i < mean.length; i++) {
                    mean[i] += result[i] / runs;
                }
            }
            printRow(value, mean);
        }

        restoreParameters(base);
    }

    // Builds the model from the current parameter values, runs it once and returns its outputs.
    private static double[] runModel() {
        Create create = new Create(createDelay);
        Process process1 = new Process(delay1, maxQueue1);
        Process process2 = new Process(delay2, maxQueue2);
        Process process3 = new Process(delay3, maxQueue3);
        Dispose dispose = new Dispose();

        create.next = process1;
        process1.next = process2;
        process2.next = process3;
        process3.next = dispose;

        // On a tie, complete downstream service before accepting an upstream item.
        Model model = new Model(process3, process2, process1, create, dispose);
        model.simulate(simulationTime);

        double[] result = new double[Result.SIZE];
        result[Result.CREATED] = create.quantity;
        result[Result.DISPOSED] = dispose.quantity;
        result[Result.REJECTED_1] = process1.failure;
        result[Result.REJECTED_2] = process2.failure;
        result[Result.REJECTED_3] = process3.failure;
        int rejected = process1.failure + process2.failure + process3.failure;
        result[Result.REJECTION_PROBABILITY] = create.quantity > 0 ? (double) rejected / create.quantity : 0.0;
        result[Result.LOAD_1] = process1.getMeanLoad();
        result[Result.LOAD_2] = process2.getMeanLoad();
        result[Result.LOAD_3] = process3.getMeanLoad();
        result[Result.QUEUE_1] = process1.getMeanQueue();
        result[Result.QUEUE_2] = process2.getMeanQueue();
        result[Result.QUEUE_3] = process3.getMeanQueue();
        return result;
    }

    private static void printRow(double value, double[] mean) {
        String valueText = value == Math.rint(value) ? String.format("%.0f", value) : String.format("%.2f", value);
        System.out.printf("| %s | %.1f | %.1f | %.1f | %.1f | %.1f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f |%n",
                valueText,
                mean[Result.CREATED], mean[Result.DISPOSED],
                mean[Result.REJECTED_1], mean[Result.REJECTED_2], mean[Result.REJECTED_3],
                mean[Result.REJECTION_PROBABILITY],
                mean[Result.LOAD_1], mean[Result.LOAD_2], mean[Result.LOAD_3],
                mean[Result.QUEUE_1], mean[Result.QUEUE_2], mean[Result.QUEUE_3]);
    }

    private static double[] saveParameters() {
        return new double[] {createDelay, delay1, delay2, delay3, maxQueue1, maxQueue2, maxQueue3, simulationTime};
    }

    private static void restoreParameters(double[] base) {
        createDelay = base[0];
        delay1 = base[1];
        delay2 = base[2];
        delay3 = base[3];
        maxQueue1 = (int) base[4];
        maxQueue2 = (int) base[5];
        maxQueue3 = (int) base[6];
        simulationTime = base[7];
    }

    private static double[] toDouble(int[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }

    // Sets one model parameter to the given value.
    private interface Setter {
        void set(double value);
    }

    // Positions of the model outputs in the result array.
    private static final class Result {
        static final int CREATED = 0;
        static final int DISPOSED = 1;
        static final int REJECTED_1 = 2;
        static final int REJECTED_2 = 3;
        static final int REJECTED_3 = 4;
        static final int REJECTION_PROBABILITY = 5;
        static final int LOAD_1 = 6;
        static final int LOAD_2 = 7;
        static final int LOAD_3 = 8;
        static final int QUEUE_1 = 9;
        static final int QUEUE_2 = 10;
        static final int QUEUE_3 = 11;
        static final int SIZE = 12;
    }
}

/**
 * Common base for every element of the model.
 *
 * Each element knows when its own next event will happen (nextTime).
 * The Model looks at all elements, picks the one with the smallest nextTime,
 * moves the clock there, and calls that element's outAct().
 */
abstract class Element {
    // Current model time. The Model updates it for all elements before each event.
    protected double time;
    // Time of this element's next event. POSITIVE_INFINITY means "no event planned".
    protected double nextTime = Double.POSITIVE_INFINITY;
    // Average delay between this element's events (arrival interval or service time).
    protected final double meanDelay;
    // Element that receives items from this one (null if nothing follows).
    protected Element next;
    // How many events this element has completed (items created or items served).
    protected int quantity;

    Element(double meanDelay) {
        this.meanDelay = meanDelay;
    }

    /**
     * Returns a random delay with an exponential distribution and the given mean.
     *
     * Uses the inverse transform method: if u is uniform on (0, 1),
     * then -mean * ln(u) is exponential with that mean.
     * Math.random() can return exactly 0.0, and ln(0) is -infinity,
     * so the loop draws again in that case.
     */
    protected double getDelay() {
        double u;
        do {
            u = Math.random();
        } while (u == 0.0);
        return -meanDelay * Math.log(u);
    }

    /**
     * Called when an item enters this element from the previous one.
     * Does nothing by default; Create has no input, so it does not override it.
     */
    public void inAct() { }

    // Collect statistics for the interval before the next event changes the state.
    public void doStatistics(double delta) { }

    /**
     * Called by the Model when this element's planned event happens (time == nextTime).
     * Each subclass must say what its event does and plan the next one.
     */
    public abstract void outAct();
}

/**
 * Source of items. Its event is "a new item arrives".
 */
class Create extends Element {
    Create(double meanDelay) {
        super(meanDelay);
        // The first item arrives right at the start of the simulation.
        nextTime = 0.0;
    }

    @Override
    public void outAct() {
        // Count the new item.
        quantity++;
        // Plan the next arrival after a random interval.
        nextTime = time + getDelay();
        // Hand the item to the next element (the Process).
        next.inAct();
    }
}

/**
 * Single server with a bounded waiting queue. Its event is "service finished".
 *
 * State:
 *   busy  - true while an item is being served
 *   queue - number of items waiting (never more than maxQueue)
 */
class Process extends Element {
    // Largest number of items allowed to wait in the queue.
    private final int maxQueue;
    // True while the server is serving an item.
    protected boolean busy;
    // Number of items currently waiting.
    protected int queue;
    // Number of items rejected because the server was busy and the queue was full.
    protected int failure;
    // Total time the server has spent busy during the simulation.
    private double busyTime;
    // Integral of the queue length over time, used for the mean queue length.
    private double queueTime;

    Process(double meanDelay, int maxQueue) {
        super(meanDelay);
        this.maxQueue = maxQueue;
    }

    @Override
    public void doStatistics(double delta) {
        if (busy) {
            busyTime += delta;
        }
        queueTime += queue * delta;
    }

    public double getMeanLoad() {
        return time > 0.0 ? busyTime / time : 0.0;
    }

    public double getMeanQueue() {
        return time > 0.0 ? queueTime / time : 0.0;
    }

    /**
     * A new item arrives. There are three possible outcomes.
     */
    @Override
    public void inAct() {
        if (!busy) {
            // 1. Server is free: start serving the item now
            //    and plan the moment its service ends.
            busy = true;
            nextTime = time + getDelay();
        } else if (queue < maxQueue) {
            // 2. Server is busy but there is room: the item waits in the queue.
            //    nextTime does not change, the current service is still running.
            queue++;
        } else {
            // 3. Server is busy and the queue is full: the item is lost.
            failure++;
        }
    }

    /**
     * The current service has finished.
     */
    @Override
    public void outAct() {
        // Count the item that was just served.
        quantity++;
        if (queue > 0) {
            // Someone is waiting: take the next item from the queue
            // and start its service immediately (server stays busy).
            queue--;
            nextTime = time + getDelay();
        } else {
            // Queue is empty: server becomes idle.
            // No service is running, so there is no next "service finished" event
            // until a new item arrives (see inAct).
            busy = false;
            nextTime = Double.POSITIVE_INFINITY;
        }
        // Forward the completed item to the next stage.
        next.inAct();
    }
}

/**
 * Exit from the system: count items that completed all three stages.
 */
class Dispose extends Element {
    Dispose() {
        super(0.0);
    }

    @Override
    public void inAct() {
        quantity++;
    }

    @Override
    public void outAct() { }
}

/**
 * Runs the simulation: repeatedly finds the nearest event and executes it.
 */
class Model {
    private final Element[] elements;

    Model(Element... elements) {
        this.elements = elements;
    }

    public void simulate(double endTime) {
        while (true) {
            // Step 1: find the element whose next event is the earliest.
            // The strict "<" means that on equal times the element that comes
            // first in the array wins (see the note in main()).
            Element event = null;
            double nextTime = Double.POSITIVE_INFINITY;
            for (Element element : elements) {
                if (element.nextTime < nextTime) {
                    nextTime = element.nextTime;
                    event = element;
                }
            }

            // Step 2: collect statistics before changing the state, including
            // the final interval from the last event to the simulation end.
            double statisticsTime = Math.min(nextTime, endTime);
            for (Element element : elements) {
                element.doStatistics(statisticsTime - element.time);
                element.time = statisticsTime;
            }

            // Step 3: stop if no events are planned, or the next one is after the end time.
            if (event == null || nextTime > endTime) {
                break;
            }

            // Step 4: handle the event. This updates statistics and plans new events.
            event.outAct();
        }
    }
}
