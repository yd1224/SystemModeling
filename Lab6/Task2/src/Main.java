import java.util.*;

public class Main {
    public static void main(String[] args) {
        // Use a decimal point in printed numbers regardless of the computer's regional settings.
        Locale.setDefault(Locale.ROOT);
        // --verify runs the controlled verification cases instead of the normal experiment.
        if (args.length > 0 && args[0].equals("--verify")) { Verification.main(new String[0]); return; }
        // Optional arguments: observation time in minutes, then the random seed.
        // With no arguments, use 10,000 minutes and a repeatable default seed.
        double horizon = args.length > 0 ? Double.parseDouble(args[0]) : 10000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20261101;
        // 4 arrivals/minute -> interval 0.25; exponential processing has mean 1 minute.
        // var lets Java infer the type ConveyorModel.Result from run()'s return type.
        var r = new ConveyorModel(.25, 1, true, seed).run(horizon);
        // Utilization is a fraction: 0.8 means the device was busy for 80% of observation time.
        System.out.printf("T = %.2f min; seed = %d%n", horizon, seed);
        System.out.printf("Arrived: %d; completed: %d; remaining: %d%n", r.arrivals(), r.completed(), r.remaining());
        System.out.printf("Mean residence time: %.6f min%nThroughput: %.6f parts/min%n", r.residence(), r.throughput());
        for (int i = 0; i < 5; i++) System.out.printf("Device %d utilization: %.6f%n", i + 1, r.utilization()[i]);
        System.out.printf("Mean busy devices: %.6f%nMean parts in system: %.6f%n", r.meanBusy(), r.meanPopulation());
        System.out.printf("Returns after device 5: %d; max simultaneous movements per transition: %d%n", r.returns(), r.maxMoving());
    }
}
