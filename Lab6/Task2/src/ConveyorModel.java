import ua.stetsenkoinna.petriobj.*;
import java.util.*;

/** Lab 6, task 2. Time unit: minute. PetriSim owns transition selection and firing. */
public final class ConveyorModel {
    // Ordinary Petri tokens have no identity. Keep the first arrival time separately
    // so a part returning to device 1 does not lose its original time of entry.
    record Job(long id, double arrival) {}
    // A record groups the values returned to Main; utilization contains five fractions (0..1).
    public record Result(long arrivals, long completed, long remaining, double residence,
                         double throughput, double[] utilization, double meanBusy,
                         double meanPopulation, long returns, int maxMoving) {}

    // These four lists describe the network structure, just like positions and arrows in the diagram.
    final ArrayList<PetriP> places = new ArrayList<>();
    final ArrayList<PetriT> transitions = new ArrayList<>();
    final ArrayList<ArcIn> inputs = new ArrayList<>();
    final ArrayList<ArcOut> outputs = new ArrayList<>();
    // jobs[p] holds identities of part tokens in position p. This is bookkeeping,
    // not a waiting queue for service: Near is emptied by Start or Skip at the same time.
    final ArrayList<ArrayDeque<Job>> jobs = new ArrayList<>();
    // Array values are indexes into places, NOT token counts. Index 0 refers to device 1.
    final int[] near = new int[5], free = new int[5], processing = new int[5], moving = new int[5];
    final long[] starts = new long[5];
    final Random[] random = new Random[5];
    // Accumulated busy minutes for each device; divide by observation time to get utilization.
    final double[] busyArea = new double[5];
    final double service;
    final boolean exponential;
    final int generator, done;
    final PetriSim sim;
    long arrivals, completed, returns;
    // Sum of completed parts' residence times; integral of the number of parts over time.
    double residenceSum, populationArea;
    int maxMoving;

    /**
     * Build the net without running it. All times are minutes.
     * interval: time between arrivals; service: mean exponential or fixed processing time.
     * exponential=false is used for predictable verification cases; seed reproduces random times.
     */
    public ConveyorModel(double interval, double service, boolean exponential, long seed) {
        // Reject negative processing times, nonpositive arrival intervals, NaN, and infinity.
        if (!(interval > 0) || !Double.isFinite(interval) || service < 0 || !Double.isFinite(service))
            throw new IllegalArgumentException("Invalid arrival/service parameter");
        this.service = service;
        this.exponential = exponential;
        // Each new model needs element numbering starting from zero, including during verification.
        PetriP.initNext(); PetriT.initNext(); ArcIn.initNext(); ArcOut.initNext();
        // One generator token enables repeated arrivals; no parts are initially completed.
        generator = place("Generator", 1);
        done = place("Completed", 0);
        // Four positions per device; only Free initially has a token (one available device).
        for (int i = 0; i < 5; i++) {
            near[i] = place("Near" + (i + 1), 0);
            free[i] = place("Free" + (i + 1), 1);
            processing[i] = place("Processing" + (i + 1), 0);
            moving[i] = place("Moving" + (i + 1), 0);
            // Different repeatable random sequences for different devices; L denotes a long integer.
            random[i] = new Random(seed + 104729L * i);
        }
        // Service completion comes before arrivals for equal timestamps. All due outputs
        // are delivered by PetriSim.output() before a new input/conflict resolution.
        for (int i = 0; i < 5; i++) {
            TrackTransition finish = transition("Finish" + (i + 1), service, 0, processing[i], done, i, "finish");
            // Processing -> Finish -> Completed + Free. The part stays INSIDE Finish
            // during its delay; Free is returned only when processing actually ends.
            in(processing[i], finish); out(finish, done); out(finish, free[i]);
        }
        // -1 means no existing source Job and no associated device, not a Petri position.
        TrackTransition arrival = transition("Arrival", interval, 0, -1, near[0], -1, "arrival");
        // Every interval: return the generator token and create a part near device 1.
        in(generator, arrival); out(arrival, generator); out(arrival, near[0]);
        for (int i = 0; i < 5; i++) {
            // Start requires BOTH a part and a free resource. Priority 1 beats Skip's 0.
            TrackTransition start = transition("Start" + (i + 1), 0, 1, near[i], processing[i], i, "start");
            in(near[i], start); in(free[i], start); out(start, processing[i]);
            // If the device is busy, Start is disabled and Skip sends the part onward immediately.
            TrackTransition skip = transition("Skip" + (i + 1), 0, 0, near[i], moving[i], i, "skip");
            in(near[i], skip); out(skip, moving[i]);
            // ?: chooses 5 minutes after device 5, otherwise 1 minute.
            // % wraps the next index: (4 + 1) % 5 = 0, so device 5 returns to device 1.
            // No resource input limits transport: multiple parts can move at once.
            TrackTransition move = transition("Move" + (i + 1), i == 4 ? 5 : 1, 0,
                    moving[i], near[(i + 1) % 5], i, "move");
            in(moving[i], move); out(move, near[(i + 1) % 5]);
        }
        // Convert lists to the arrays required by the library and assemble the full network.
        PetriNet net = new PetriNet("Conveyor5", places.toArray(PetriP[]::new),
                transitions.toArray(PetriT[]::new), inputs.toArray(ArcIn[]::new), outputs.toArray(ArcOut[]::new));
        sim = new PetriSim(net);
    }

    // Create a position with mark initial tokens, plus its empty identity list; return its index.
    int place(String name, int mark) {
        places.add(new PetriP(name, mark)); jobs.add(new ArrayDeque<>()); return places.size() - 1;
    }
    // source/target describe the part's bookkeeping route; arcs below define actual Petri behavior.
    TrackTransition transition(String name, double delay, int priority, int source, int target, int device, String kind) {
        TrackTransition t = new TrackTransition(name, delay, priority, source, target, device, kind);
        transitions.add(t); return t;
    }
    // Input arc: position -> transition. Output arc: transition -> position. Each carries one token.
    void in(int p, PetriT t) { inputs.add(new ArcIn(places.get(p), t, 1)); }
    void out(PetriT t, int p) { outputs.add(new ArcOut(t, places.get(p), 1)); }

    /** Metadata observes actual PetriT firings; it never chooses a route or schedules events. */
    final class TrackTransition extends PetriT {
        final int source, target, device;
        final String kind;
        // One entry per active transition channel, aligned with the library's channel indexes.
        final ArrayList<Job> pending = new ArrayList<>();
        TrackTransition(String name, double delay, int priority, int source, int target, int device, String kind) {
            super(name, delay, priority);
            this.source = source; this.target = target; this.device = device; this.kind = kind;
            if (kind.equals("finish") && exponential) setDistribution("exp", service);
        }
        @Override public double getTimeServ() {
            // Seeded equivalent of FunRand.exp: independent stream per device.
            // For a uniform U in [0,1), -mean * ln(1-U) has the required exponential distribution.
            // Arrival, movement, and instantaneous transitions keep their fixed delay.
            return kind.equals("finish") && exponential
                    ? -service * Math.log(1.0 - random[device].nextDouble()) : getParameter();
        }
        @Override public void actIn(PetriP[] pp, double time) {
            if (!condition(pp)) return;
            // Move the identity alongside the consumed part token. Arrival has no existing part.
            Job job = source < 0 ? null : jobs.get(source).removeFirst();
            // The parent PetriT consumes input tokens and schedules the timed output.
            super.actIn(pp, time);
            pending.add(job);
            if (kind.equals("start")) starts[device]++;
            if (kind.equals("skip") && device == 4) returns++;
            if (kind.equals("move")) maxMoving = Math.max(maxMoving, getBuffer());
        }
        @Override public void actOut(PetriP[] pp, double time) {
            if (getBuffer() == 0) return;
            // getNum() identifies the channel finishing now; it need not be the oldest channel.
            Job job = pending.remove(getNum());
            // The parent PetriT produces output tokens and removes the finished channel.
            super.actOut(pp, time);
            if (kind.equals("arrival")) job = new Job(++arrivals, time);
            if (kind.equals("finish")) {
                // Example: first arrival 0.5, completion 2.3 -> residence time 1.8 minutes.
                completed++; residenceSum += time - job.arrival();
            } else jobs.get(target).addLast(job);
        }

    }

    /** Run a newly constructed model once, from time 0 through horizon (inclusive). */
    public Result run(double horizon) {
        if (!(horizon > 0) || !Double.isFinite(horizon)) throw new IllegalArgumentException("Invalid horizon");
        sim.setSimulationTime(horizon);
        sim.setTimeCurr(0);
        // Several zero-delay events at one timestamp are normal; an endless cycle is not.
        long sameTimeSteps = 0;
        while (true) {
            sim.input(); // Real PetriSim conflict resolution, priority and multichannel input.
            verify();
            // No state changes between now and the nearest scheduled transition output, next.
            double now = sim.getCurrentTime(), next = sim.getTimeMin();
            // Clip the final interval: now=99, next=103, horizon=100 -> dt=1, not 4.
            double end = Math.min(next, horizon), dt = end - now;
            if (dt < 0) throw new IllegalStateException("Time moved backwards");
            // Time-weighted population: 3 parts present for 2 minutes contribute 6 part-minutes.
            populationArea += (arrivals - completed) * dt;
            // Free has 1 token when idle and 0 when busy, so (1-mark) is the busy indicator.
            for (int i = 0; i < 5; i++) busyArea[i] += (1 - places.get(free[i]).getMark()) * dt;
            // Statistics now reach horizon. Do not execute a later event; an event exactly at T is included.
            if (next > horizon) { sim.setTimeCurr(horizon); break; }
            if (dt == 0 && ++sameTimeSteps > 1000000) throw new IllegalStateException("Zero-time cycle");
            if (dt > 0) sameTimeSteps = 0;
            // Jump directly to the event instead of advancing the clock in tiny fixed steps.
            sim.setTimeCurr(next);
            sim.output(); // Actual PetriSim timed outputs, including simultaneous completions.
            verify();
        }
        // Divide each busy-time total by T: 75 busy minutes out of 100 gives utilization 0.75.
        double[] utilization = Arrays.stream(busyArea).map(a -> a / horizon).toArray();
        // Residence averages only completed parts; NaN means no completions exist to average.
        // Throughput = completed/T; mean busy devices = sum of utilizations; mean population = area/T.
        return new Result(arrivals, completed, arrivals - completed,
                completed == 0 ? Double.NaN : residenceSum / completed, completed / horizon,
                utilization, Arrays.stream(utilization).sum(), populationArea / horizon, returns, maxMoving);
    }

    // Check accounting after token inputs and outputs, including zero-duration intermediate states.
    void verify() {
        long active = 0;
        for (int p = 0; p < places.size(); p++) {
            require(places.get(p).getMark() >= 0, "Negative marking");
            // Generator/resource tokens are not parts; Completed is counted separately.
            if (p != generator && p != done && !isFree(p)) {
                require(places.get(p).getMark() == jobs.get(p).size(), "Place metadata mismatch");
                active += places.get(p).getMark();
            }
        }
        // Parts currently being processed or transported are in transition buffers, not positions.
        for (PetriT base : transitions) {
            TrackTransition t = (TrackTransition) base;
            require(t.pending.size() == t.getBuffer(), "Channel metadata mismatch");
            // A generator channel represents the NEXT arrival, which has not entered the system yet.
            if (!t.kind.equals("arrival")) active += t.getBuffer();
        }
        // No parts may disappear or be duplicated: arrived = completed + still inside.
        require(arrivals == completed + active, "Part conservation failed");
        require(places.get(done).getMark() == completed, "Completion counter mismatch");
        for (int i = 0; i < 5; i++) {
            // Device resource is either free or held by Start, Processing, or Finish, exactly once.
            int occupied = places.get(processing[i]).getMark();
            for (PetriT base : transitions) {
                TrackTransition t = (TrackTransition) base;
                if (t.device == i && (t.kind.equals("start") || t.kind.equals("finish"))) occupied += t.getBuffer();
            }
            require(places.get(free[i]).getMark() + occupied == 1, "Device capacity failed");
        }
    }
    boolean isFree(int p) { for (int v : free) if (v == p) return true; return false; }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
