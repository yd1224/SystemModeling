import java.util.*;

/** Reproducible boundary cases, plus conservation checked at every event. */
public class Verification {
    static void close(double actual, double expected) {
        // Floating-point calculations can differ by tiny rounding errors; allow tolerance 1e-8.
        ConveyorModel.require(Math.abs(actual - expected) < 1e-8, actual + " != " + expected);
    }
    public static void main(String[] args) {
        // Observation ends before the first arrival at 0.25: no parts and no busy devices.
        var empty = new ConveyorModel(.25, 1, true, 1).run(.1);
        ConveyorModel.require(empty.arrivals() == 0 && empty.completed() == 0, "Empty system");
        close(empty.meanBusy(), 0);
        // Fixed service 0.1 < interval 0.25: every part uses device 1, without skipping.
        // At T=10 the 40th part has just arrived; 39 completed, giving busy time 39*0.1=3.9.
        var fast = new ConveyorModel(.25, .1, false, 1).run(10);
        ConveyorModel.require(fast.arrivals() == 40 && fast.completed() == 39, "Fast service counts");
        close(fast.residence(), .1); close(fast.utilization()[0], .39);
        for (int i = 1; i < 5; i++) close(fast.utilization()[i], 0);
        // Each completion coincides with the next arrival. Free the device before accepting a part.
        var tie = new ConveyorModel(.25, .25, false, 1).run(10);
        ConveyorModel.require(tie.completed() == 39, "Equal-time completion");
        close(tie.residence(), .25); close(tie.utilization()[1], 0);
        // Very slow service occupies all devices and forces circulation and overlapping movements.
        var slow = new ConveyorModel(.25, 100, false, 1).run(20);
        ConveyorModel.require(slow.completed() == 0 && slow.returns() > 0 && slow.maxMoving() > 1,
                "Return path or multichannel transport missing");
        // A finite horizon must not integrate statistics up to the next event.
        // Only 0.05 minutes of activity fit between first arrival 0.25 and the observation end 0.3.
        var clipped = new ConveyorModel(.25, 1, false, 1).run(.3);
        close(clipped.utilization()[0], 1.0 / 6); close(clipped.meanPopulation(), 1.0 / 6);
        // Same seed and parameters must reproduce processing times and resulting statistics.
        var a = new ConveyorModel(.25, 1, true, 42).run(1000);
        var b = new ConveyorModel(.25, 1, true, 42).run(1000);
        ConveyorModel.require(a.completed() == b.completed() && a.residence() == b.residence()
                && Arrays.equals(a.utilization(), b.utilization()), "Seed reproducibility");
        ConveyorModel.require(a.returns() > 0 && a.meanBusy() <= 5, "Stochastic sanity");
        System.out.println("PASS: empty; fast service; priority; simultaneous events; return loop; multichannel movement; horizon clipping; seeded repeatability; event-by-event conservation and capacity.");
    }
}
