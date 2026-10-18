/**
 * Figure 2.1: Create -> Process 1 -> Process 2 -> Process 3 -> Dispose.
 */
public class Main {
    public static void main(String[] args) {
        // Example parameters inherited from Task2; the figure specifies the route.
        Create create = new Create(2.0);
        Process process1 = new Process(1.0, 5);
        Process process2 = new Process(1.0, 5);
        Process process3 = new Process(1.0, 5);
        Dispose dispose = new Dispose();

        create.next = process1;
        process1.next = process2;
        process2.next = process3;
        process3.next = dispose;

        // On a tie, complete downstream service before accepting an upstream item.
        Model model = new Model(process3, process2, process1, create, dispose);
        model.simulate(1000.0);

        System.out.println("Created: " + create.quantity);
        printProcess("Process 1", process1);
        printProcess("Process 2", process2);
        printProcess("Process 3", process3);
        System.out.println("Disposed: " + dispose.quantity);
    }

    private static void printProcess(String name, Process process) {
        System.out.println("\n" + name);
        System.out.println("Served: " + process.quantity);
        System.out.println("Rejected: " + process.failure);
        System.out.println("In queue: " + process.queue);
        System.out.println("Busy: " + process.busy);
        System.out.printf("Mean utilization: %.2f%%%n", process.getMeanLoad() * 100);
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

    Process(double meanDelay, int maxQueue) {
        super(meanDelay);
        this.maxQueue = maxQueue;
    }

    @Override
    public void doStatistics(double delta) {
        if (busy) {
            busyTime += delta;
        }
    }

    public double getMeanLoad() {
        return time > 0.0 ? busyTime / time : 0.0;
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
