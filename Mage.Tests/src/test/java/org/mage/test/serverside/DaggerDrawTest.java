package org.mage.test.serverside;

import org.junit.Assert;
import org.junit.Test;
import org.mage.test.serverside.base.FullGameSimulationInstrumentedBase;

/**
 * DAgger mixture mode: the per-game pilot draw must be a pure deterministic
 * function of (gameSeed, fraction) — same seed always yields the same pilot,
 * the boundary fractions are exact, and the realized student share converges
 * to the fraction.  See memory-bank/progress/dagger_phase2_collection.md.
 */
public class DaggerDrawTest {

    @Test
    public void sameSeedSameFractionIsDeterministic() {
        for (long seed : new long[]{0L, 42L, -1L, Long.MAX_VALUE, 0xDEADBEEFL}) {
            for (double f : new double[]{0.1, 0.25, 0.5, 0.83}) {
                boolean first = FullGameSimulationInstrumentedBase.daggerStudentDraw(seed, f);
                for (int i = 0; i < 5; i++) {
                    Assert.assertEquals(
                            "draw must be reproducible for seed=" + seed + " f=" + f,
                            first,
                            FullGameSimulationInstrumentedBase.daggerStudentDraw(seed, f));
                }
            }
        }
    }

    @Test
    public void fractionZeroIsAlwaysTeacher() {
        for (long seed = -500; seed < 500; seed++) {
            Assert.assertFalse(FullGameSimulationInstrumentedBase.daggerStudentDraw(seed, 0.0));
        }
    }

    @Test
    public void fractionOneIsAlwaysStudent() {
        for (long seed = -500; seed < 500; seed++) {
            Assert.assertTrue(FullGameSimulationInstrumentedBase.daggerStudentDraw(seed, 1.0));
        }
    }

    @Test
    public void realizedShareConvergesToFraction() {
        double fraction = 0.3;
        int n = 20000;
        int students = 0;
        for (long seed = 0; seed < n; seed++) {
            if (FullGameSimulationInstrumentedBase.daggerStudentDraw(seed, fraction)) {
                students++;
            }
        }
        double realized = students / (double) n;
        Assert.assertEquals("realized student share should track the fraction",
                fraction, realized, 0.02);
    }

    @Test
    public void differentSeedsProduceBothPilots() {
        boolean sawStudent = false;
        boolean sawTeacher = false;
        for (long seed = 0; seed < 100 && !(sawStudent && sawTeacher); seed++) {
            if (FullGameSimulationInstrumentedBase.daggerStudentDraw(seed, 0.5)) {
                sawStudent = true;
            } else {
                sawTeacher = true;
            }
        }
        Assert.assertTrue(sawStudent);
        Assert.assertTrue(sawTeacher);
    }
}
