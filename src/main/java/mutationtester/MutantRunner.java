package mutationtester;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;

import mutationtester.coverage.CoverageData;
import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;

/**
 * Tests every mutant, one at a time or with several workers.
 *
 * With more than one worker, each extra worker gets its own copy of the
 * prepared working copy (made after the baseline, so builds are warm) and the
 * workers take mutants from a shared queue. A mutant's file is found in the
 * worker's copy by its path relative to the main copy. Results keep the
 * original order, so the report is the same however the work was split.
 *
 * Before each mutant the {@link RunControl} is checked: once the time limit
 * passes or the run is cancelled, no new mutants start. Mutants already
 * running finish and count, except after a cancel, where their test processes
 * were killed and the result would be wrong.
 */
final class MutantRunner {

    record Outcome(List<MutantResult> results, int notTested, RunControl.StopReason stopReason) {
        boolean stoppedEarly() {
            return notTested > 0;
        }
    }

    private MutantRunner() {
    }

    static Outcome run(List<MutationPoint> points, Path mainCopy, String compileCommand, String testCommand,
            CoverageData coverageData, int workers, RunControl control, boolean showType) {
        int total = points.size();
        MutantResult[] slots = new MutantResult[total];
        List<Path> copies = new ArrayList<>(List.of(mainCopy));

        try {
            for (int i = 2; i <= workers; i++) {
                Path copy = mainCopy.resolveSibling(mainCopy.getFileName() + "-w" + i);
                try {
                    FileUtils.copyDirectory(mainCopy.toFile(), copy.toFile());
                    copies.add(copy);
                } catch (IOException e) {
                    System.err.println("WARNING: Could not create worker copy " + copy + " (" + e.getMessage()
                            + "); continuing with " + copies.size() + " worker(s).");
                    break;
                }
            }

            if (copies.size() == 1) {
                runSequentially(points, slots, mainCopy, compileCommand, testCommand, coverageData, control, showType);
            } else {
                System.out.println("Testing mutants with " + copies.size() + " parallel workers");
                runInParallel(points, slots, copies, compileCommand, testCommand, coverageData, control, showType);
            }
        } finally {
            if (!control.isCancelled()) {
                for (Path copy : copies.subList(1, copies.size())) {
                    FileUtils.deleteQuietly(copy.toFile());
                }
            }
        }

        List<MutantResult> results = new ArrayList<>();
        for (MutantResult slot : slots) {
            if (slot != null) {
                results.add(slot);
            }
        }
        int notTested = total - results.size();
        return new Outcome(results, notTested, notTested > 0 ? control.stopReason() : null);
    }

    private static void runSequentially(List<MutationPoint> points, MutantResult[] slots, Path copy,
            String compileCommand, String testCommand, CoverageData coverageData, RunControl control,
            boolean showType) {
        for (int i = 0; i < points.size(); i++) {
            if (control.shouldStop()) {
                return;
            }
            MutationPoint point = points.get(i);
            System.out.print("[" + (i + 1) + "/" + points.size() + "] Testing " + describe(point, showType) + "... ");
            MutantResult result = Main.testSingleMutant(point, copy, compileCommand, testCommand, coverageData);
            if (control.isCancelled()) {
                System.out.println("CANCELLED");
                return;
            }
            slots[i] = result;
            System.out.println(statusText(result));
        }
    }

    private static void runInParallel(List<MutationPoint> points, MutantResult[] slots, List<Path> copies,
            String compileCommand, String testCommand, CoverageData coverageData, RunControl control,
            boolean showType) {
        Path mainCopy = copies.get(0).toAbsolutePath().normalize();
        AtomicInteger next = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(copies.size());

        for (Path copy : copies) {
            Path workerCopy = copy.toAbsolutePath().normalize();
            pool.submit(() -> {
                Main.QUIET.set(true); // one line per mutant instead of interleaved process logs
                while (!control.shouldStop()) {
                    int i = next.getAndIncrement();
                    if (i >= points.size()) {
                        return;
                    }
                    MutationPoint point = points.get(i);
                    MutantResult result;
                    try {
                        File file = workerCopy.resolve(
                                mainCopy.relativize(point.getSourceFile().toPath().toAbsolutePath().normalize())).toFile();
                        result = Main.testSingleMutant(point, file, workerCopy, compileCommand, testCommand, coverageData);
                    } catch (RuntimeException e) {
                        result = new MutantResult(point, MutantResult.MutantStatus.COMPILE_ERROR,
                                "Internal error: " + e, false);
                    }
                    if (control.isCancelled()) {
                        return;
                    }
                    slots[i] = result;
                    String line = "[" + done.incrementAndGet() + "/" + points.size() + "] "
                            + describe(point, showType) + "... " + statusText(result);
                    synchronized (System.out) {
                        System.out.println(line);
                    }
                }
            });
        }

        pool.shutdown();
        try {
            while (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                // Keep waiting; each command has its own timeout in Main.runCommand.
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    private static String describe(MutationPoint point, boolean showType) {
        String label = point.getCustomLabel() != null ? " [" + point.getCustomLabel() + "]" : "";
        String type = showType && point.getType() != null ? " [" + point.getType().name() + "]" : "";
        return point.getSourceFile().getName() + ":" + point.getLineNumber() + ":" + point.getColumnNumber()
                + " (" + point.getOperator() + " to " + point.getReplacement() + ")" + type + label;
    }

    private static String statusText(MutantResult result) {
        return switch (result.getStatus()) {
            case KILLED -> "KILLED";
            case SURVIVED -> "SURVIVED";
            case NO_COVERAGE -> "NO COVERAGE";
            case COMPILE_ERROR -> "COMPILE ERROR";
            default -> "TIMEOUT OR ERROR";
        };
    }
}
