package task3;

import ua.stetsenkoinna.petriobj.*;
import java.util.*;

/** Lab 6, task 3: the inventory net from Lab 5, task 4. All times are weeks. */
public final class InventoryModel {
    static final int TARGET = 72, REORDER_POINT = 18;
    public record Result(double time, long customers, int sold, int lost, int backlog,
                         int stock, int outstanding, int orders, long ordered, long received,
                         double meanStock, double meanLostInterval) {}

    final ArrayList<PetriP> places = new ArrayList<>();
    final ArrayList<PetriT> transitions = new ArrayList<>();
    final ArrayList<ArcIn> inputs = new ArrayList<>();
    final ArrayList<ArcOut> outputs = new ArrayList<>();
    final int initialStock;
    final PetriP customerGenerator, customer, stock, sold, shortage, lost, backlog, deficit;
    final PetriP reviewGenerator, reviewDue, forming, skipped, orders, outstanding;
    final PetriP dispatch, arrived, mutex;
    final PetriT arrival, review, sale, noStock, lose, defer, fulfill;
    final PetriT startOrder, skipOrder, addUnit, finishOrder, delivery, receive;
    final PetriSim sim;
    long customers, orderedUnits, receivedUnits;
    double firstLost = Double.NaN, lastLost = Double.NaN, stockArea;
    boolean hasRun;

    /** Normal task parameters; seed makes arrivals and loss/backorder decisions reproducible. */
    public InventoryModel(long seed) { this(.2, true, 72, .8, seed); }

    // Configurable parameters are used only to create predictable verification examples.
    InventoryModel(double interval, boolean exponential, int initialStock, double lossProbability, long seed) {
        if (!(interval > 0) || !Double.isFinite(interval) || initialStock < 0 || initialStock > TARGET
                || !Double.isFinite(lossProbability) || lossProbability < 0 || lossProbability > 1)
            throw new IllegalArgumentException("Invalid inventory parameters");
        this.initialStock = initialStock;
        Random arrivalRandom = new Random(seed), routingRandom = new Random(seed ^ 0x5DEECE66DL);
        PetriP.initNext(); PetriT.initNext(); ArcIn.initNext(); ArcOut.initNext();
        customerGenerator = place("CustomerGenerator", 1);
        customer = place("Customer", 0);
        stock = place("H_OnHand", initialStock);
        sold = place("Sold", 0);
        shortage = place("CustomerWithoutStock", 0);
        lost = place("LostSales", 0);
        backlog = place("B_Backorders", 0);
        deficit = place("D_Deficit", TARGET - initialStock);
        reviewGenerator = place("ReviewGenerator", 1);
        reviewDue = place("ReviewDue", 0);
        forming = place("FormingOrder", 0);
        skipped = place("ReviewsWithoutOrder", 0);
        orders = place("Orders", 0);
        outstanding = place("O_Outstanding", 0);
        dispatch = place("UnitsToDispatch", 0);
        arrived = place("UnitsArrived", 0);
        // One shared token makes each immediate operation atomic. It is not a refrigerator.
        mutex = place("M_Control", 1);

        arrival = new PetriT("CustomerArrival", interval) {
            @Override public double getTimeServ() {
                return exponential ? -interval * Math.log(1 - arrivalRandom.nextDouble()) : interval;
            }
        };
        if (exponential) arrival.setDistribution("exp", interval);
        transitions.add(arrival);
        in(customerGenerator, arrival); out(arrival, customerGenerator); out(arrival, customer);
        review = timed("Review", 4);
        in(reviewGenerator, review); out(review, reviewGenerator); out(review, reviewDue);

        // Backorders have precedence over new sales; new sales beat the shortage fallback.
        fulfill = immediate("FulfillBackorder", 3);
        in(backlog, fulfill); in(stock, fulfill); out(fulfill, sold);
        sale = immediate("SellFromStock", 2);
        in(customer, sale); in(stock, sale); out(sale, sold); out(sale, deficit);
        noStock = timed("NoStock", 0); noStock.setPriority(1);
        // Hold M until one of the two stochastic alternatives completes. This isolates
        // the 0.8/0.2 conflict from unrelated operations at the same timestamp.
        in(mutex, noStock); in(customer, noStock); out(noStock, shortage);
        lose = timed("CustomerLeaves", 0); lose.setPriority(1); lose.setProbability(lossProbability);
        in(shortage, lose); out(lose, lost); out(lose, mutex);
        defer = timed("AcceptBackorder", 0); defer.setPriority(1); defer.setProbability(1 - lossProbability);
        in(shortage, defer); out(defer, backlog); out(defer, deficit); out(defer, mutex);

        // Hold M for the entire formation loop, so the batch equals D at the order decision.
        // New sales cannot change D halfway through constructing this instantaneous batch.
        startOrder = timed("StartOrder", 0); startOrder.setPriority(2);
        in(mutex, startOrder); in(reviewDue, startOrder); out(startOrder, forming);
        // Information arc checks D >= 54 without consuming D: H + O - B <= 18.
        inputs.add(new ArcIn(deficit, startOrder, TARGET - REORDER_POINT, true));
        skipOrder = immediate("DoNotOrder", 1);
        in(reviewDue, skipOrder); out(skipOrder, skipped);
        addUnit = timed("AddUnitToOrder", 0); addUnit.setPriority(2);
        in(forming, addUnit); in(deficit, addUnit);
        out(addUnit, forming); out(addUnit, outstanding); out(addUnit, dispatch);
        // AddUnit's higher priority repeats the zero-time loop until all D units are ordered.
        finishOrder = timed("FinishOrder", 0); finishOrder.setPriority(1);
        in(forming, finishOrder); out(finishOrder, orders); out(finishOrder, mutex);
        delivery = timed("Delivery", 3);
        in(dispatch, delivery); out(delivery, arrived);
        // No resource limits delivery: all units formed at one time arrive together 3 weeks later.
        receive = immediate("ReceiveUnit", 3);
        in(arrived, receive); in(outstanding, receive); out(receive, stock);

        PetriNet net = new PetriNet("RefrigeratorInventory", places.toArray(PetriP[]::new),
                transitions.toArray(PetriT[]::new), inputs.toArray(ArcIn[]::new), outputs.toArray(ArcOut[]::new));
        sim = new PetriSim(net) {
            @Override public PetriT doConflikt(ArrayList<PetriT> active) {
                // Preserve PetriSim's algorithm, replacing only the random draw for the
                // specified loss/backorder conflict with a seeded draw. M isolates this pair.
                if (active.contains(lose) && active.contains(defer)
                        && active.getFirst().getPriority() == lose.getPriority())
                    return routingRandom.nextDouble() < lose.getProbability() ? lose : defer;
                return super.doConflikt(active);
            }
            @Override protected void afterActOut(PetriT transition) {
                // Observers count completed events; they never change markings or schedule events.
                if (transition == arrival) customers++;
                if (transition == addUnit) orderedUnits++;
                if (transition == receive) receivedUnits++;
                if (transition == lose) {
                    if (Double.isNaN(firstLost)) firstLost = getCurrentTime();
                    lastLost = getCurrentTime();
                }
            }
        };
    }

    PetriP place(String name, int mark) {
        PetriP p = new PetriP(name, mark); places.add(p); return p;
    }
    PetriT timed(String name, double delay) {
        PetriT t = new PetriT(name, delay); transitions.add(t); return t;
    }
    PetriT immediate(String name, int priority) {
        PetriT t = timed(name, 0); t.setPriority(priority);
        in(mutex, t); out(t, mutex); return t;
    }
    void in(PetriP p, PetriT t) { inputs.add(new ArcIn(p, t, 1)); }
    void out(PetriT t, PetriP p) { outputs.add(new ArcOut(t, p, 1)); }

    /** Run a newly created model once. Events exactly at the horizon are included. */
    public Result run(double horizon) {
        if (!(horizon > 0) || !Double.isFinite(horizon) || hasRun)
            throw new IllegalArgumentException("Use a positive finite horizon and a fresh model");
        hasRun = true;
        sim.setSimulationTime(horizon); sim.setTimeCurr(0);
        long zeroTimeSteps = 0;
        while (true) {
            sim.input();
            double now = sim.getCurrentTime(), next = sim.getTimeMin();
            if (next < now) throw new IllegalStateException("Time moved backwards");
            // Zero-time operations can temporarily hold stock/control tokens in buffers.
            // Check inventory identities only after the entire current timestamp has settled.
            if (next > now) {
                verify();
                stockArea += stock.getMark() * (Math.min(next, horizon) - now);
                zeroTimeSteps = 0;
            } else if (++zeroTimeSteps > 1000000) {
                throw new IllegalStateException("Zero-time cycle");
            }
            if (next > horizon) { sim.setTimeCurr(horizon); break; }
            sim.setTimeCurr(next);
            sim.output();
        }
        return new Result(horizon, customers, sold.getMark(), lost.getMark(), backlog.getMark(),
                stock.getMark(), outstanding.getMark(), orders.getMark(), orderedUnits, receivedUnits,
                stockArea / horizon, lost.getMark() < 2 ? Double.NaN
                        : (lastLost - firstLost) / (lost.getMark() - 1));
    }

    /** These identities hold after all instantaneous operations at a timestamp finish. */
    void verify() {
        for (PetriP p : places) require(p.getMark() >= 0, "Negative marking: " + p.getName());
        for (PetriT t : transitions)
            if (t != arrival && t != review && t != delivery)
                require(t.getBuffer() == 0, "Unfinished immediate transition");
        require(mutex.getMark() == 1, "Control token lost");
        require(customer.getMark() == 0 && shortage.getMark() == 0 && forming.getMark() == 0
                && reviewDue.getMark() == 0, "Immediate routing did not finish");
        require(deficit.getMark() == TARGET - (stock.getMark() + outstanding.getMark() - backlog.getMark()),
                "D = 72 - (H + O - B) failed");
        require(customers == (long) sold.getMark() + lost.getMark() + backlog.getMark(), "Customer balance failed");
        require(initialStock + receivedUnits == (long) stock.getMark() + sold.getMark(), "Stock balance failed");
        require(outstanding.getMark() == dispatch.getMark() + delivery.getBuffer() + arrived.getMark(),
                "Outstanding shipment balance failed");
        require(orderedUnits == receivedUnits + outstanding.getMark(), "Order balance failed");
        require(stock.getMark() == 0 || backlog.getMark() == 0, "Backorders not served first");
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
