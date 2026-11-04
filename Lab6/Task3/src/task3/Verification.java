package task3;

/** Fixed inputs make the expected inventory and timing results calculable by hand. */
public class Verification {
    static void close(double actual, double expected) {
        InventoryModel.require(Math.abs(actual - expected) < 1e-8, actual + " != " + expected);
    }
    public static void main(String[] args) {
        var empty = new InventoryModel(.2, false, 72, .8, 1).run(.1);
        close(empty.meanStock(), 72);
        InventoryModel.require(empty.customers() == 0 && Double.isNaN(empty.meanLostInterval()), "Initial state");
        // At weeks 1..10 one unit is sold. The state at week 10 has zero duration in the integral.
        var sales = new InventoryModel(1, false, 72, .8, 1).run(10);
        InventoryModel.require(sales.customers() == 10 && sales.sold() == 10 && sales.stock() == 62
                && sales.orders() == 0, "Sales and no-order policy");
        close(sales.meanStock(), 67.5);
        var above = new InventoryModel(100, false, 19, .8, 1).run(4);
        InventoryModel.require(above.orders() == 0 && above.outstanding() == 0, "Do not order above 18");
        var before = new InventoryModel(100, false, 18, .8, 1).run(3.99);
        InventoryModel.require(before.orders() == 0, "Do not order before review");
        var threshold = new InventoryModel(100, false, 18, .8, 1).run(4);
        InventoryModel.require(threshold.orders() == 1 && threshold.outstanding() == 54
                && threshold.ordered() == 54 && threshold.stock() == 18, "Order exactly at threshold");
        var transit = new InventoryModel(100, false, 18, .8, 1).run(6.99);
        InventoryModel.require(transit.received() == 0 && transit.outstanding() == 54, "Delivery too early");
        var delivered = new InventoryModel(100, false, 18, .8, 1).run(7);
        InventoryModel.require(delivered.stock() == 72 && delivered.received() == 54
                && delivered.outstanding() == 0, "Three-week multichannel delivery");
        // With no stock, force each stochastic alternative separately and verify the loss gap.
        var losses = new InventoryModel(1, false, 0, 1, 1).run(3.9);
        InventoryModel.require(losses.lost() == 3 && losses.backlog() == 0, "All customers leave");
        close(losses.meanLostInterval(), 1);
        var waiting = new InventoryModel(1, false, 0, 0, 1).run(3.9);
        InventoryModel.require(waiting.backlog() == 3 && waiting.lost() == 0, "All customers wait");
        var fulfilled = new InventoryModel(1, false, 0, 0, 1).run(7);
        InventoryModel.require(fulfilled.backlog() == 0 && fulfilled.lost() == 0
                && fulfilled.sold() == 7, "Delivery, backlog and new customer at the same time");
        var split = new InventoryModel(.001, false, 0, .8, 42).run(3);
        InventoryModel.require(Math.abs((double) split.lost() / split.customers() - .8) < .03,
                "Loss/backorder proportions");
        // Stop halfway between the first and second arrivals: mean H = (72*1 + 71*0.5)/1.5.
        var clipped = new InventoryModel(1, false, 72, .8, 1).run(1.5);
        close(clipped.meanStock(), (72 + 71 * .5) / 1.5);
        var a = new InventoryModel(42).run(1000);
        var b = new InventoryModel(42).run(1000);
        InventoryModel.require(a.equals(b), "Seed reproducibility");
        System.out.println("PASS: initial state; sales; review timing; threshold 18; order quantity; delivery after 3 weeks; multichannel delivery; loss/backorder branches; backlog priority; loss intervals; 0.8/0.2 routing; horizon clipping; seeded repeatability; inventory and customer balances.");
    }
}
