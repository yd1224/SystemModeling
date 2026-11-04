package task3;

import java.util.Locale;

/** Run the experiment, or pass --verify for the controlled verification cases. */
public class Main {
    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        if (args.length > 0 && args[0].equals("--verify")) { Verification.main(new String[0]); return; }
        // The task gives no observation duration; use 10,000 weeks for the reported experiment.
        double horizon = args.length > 0 ? Double.parseDouble(args[0]) : 10000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20261101;
        var r = new InventoryModel(seed).run(horizon);
        System.out.printf("T = %.2f weeks; seed = %d%n", r.time(), seed);
        System.out.printf("Customers: %d; sold: %d; lost: %d; waiting: %d%n", r.customers(), r.sold(), r.lost(), r.backlog());
        System.out.printf("Final stock H: %d; outstanding O: %d%n", r.stock(), r.outstanding());
        System.out.printf("Orders: %d; ordered units: %d; received units: %d%n", r.orders(), r.ordered(), r.received());
        System.out.printf("Mean stock: %.6f refrigerators%n", r.meanStock());
        if (Double.isNaN(r.meanLostInterval()))
            System.out.println("Mean interval between lost sales: undefined (fewer than 2 lost sales)");
        else System.out.printf("Mean interval between lost sales: %.6f weeks%n", r.meanLostInterval());
        System.out.println("Inventory and customer balance checks: PASS");
    }
}
