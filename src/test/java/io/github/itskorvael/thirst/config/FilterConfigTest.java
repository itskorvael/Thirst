package io.github.itskorvael.thirst.config;

import io.github.itskorvael.thirst.water.WaterQuality;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterConfigTest {

    private static ThirstConfig.Filter load(String yaml, List<String> warnings) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        return ThirstConfig.load(cfg, warnings::add).filter();
    }

    @Test
    void defaultsApplyWhenTheSectionIsMissing() throws Exception {
        List<String> warnings = new ArrayList<>();
        ThirstConfig.Filter f = load("config-version: 1\n", warnings);
        assertTrue(f.enabled());
        assertEquals(6, f.uses());
        assertEquals(3, f.bucketCost());
        assertEquals(WaterQuality.NEUTRAL, f.maxQuality());
        assertEquals(ThirstConfig.DEFAULT_FILTER_RECIPE, f.recipe());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void valuesAreReadAndBadOnesAreClampedWithAWarning() throws Exception {
        List<String> warnings = new ArrayList<>();
        ThirstConfig.Filter f = load("""
                filter:
                  enabled: false
                  uses: 500
                  bucket-cost: 2
                  max-quality: fresh
                  recipe: [PITCHER_POD, SAND]
                """, warnings);
        assertFalse(f.enabled());
        assertEquals(64, f.uses());
        assertEquals(2, f.bucketCost());
        assertEquals(WaterQuality.FRESH, f.maxQuality());
        assertEquals(List.of("PITCHER_POD", "SAND"), f.recipe());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void anEmptyRecipeFallsBackToTheDefault() throws Exception {
        List<String> warnings = new ArrayList<>();
        ThirstConfig.Filter f = load("filter:\n  recipe: []\n", warnings);
        assertEquals(ThirstConfig.DEFAULT_FILTER_RECIPE, f.recipe());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void anUnknownQualityFallsBackToNeutral() throws Exception {
        List<String> warnings = new ArrayList<>();
        ThirstConfig.Filter f = load("filter:\n  max-quality: sparkling\n", warnings);
        assertEquals(WaterQuality.NEUTRAL, f.maxQuality());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void rechargeDefaultsMatchTheArchaeologyFragment() throws Exception {
        List<String> warnings = new ArrayList<>();
        ThirstConfig.Recharge r = load("config-version: 1\n", warnings).recharge();
        assertTrue(r.enabled());
        assertEquals("PRISMARINE_SHARD", r.item());
        assertEquals("archaeology:eggshell_fragment", r.key());
        assertEquals(2, r.fragments());
        assertEquals(6, r.addUses());
        assertEquals(12, r.maxUses());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void rechargeValuesAreReadAndClamped() throws Exception {
        List<String> warnings = new ArrayList<>();
        ThirstConfig.Recharge r = load("""
                filter:
                  recharge:
                    enabled: false
                    item: BONE
                    key: "other:shell"
                    fragments: 20
                    uses: 3
                    max-uses: 10
                """, warnings).recharge();
        assertFalse(r.enabled());
        assertEquals("BONE", r.item());
        assertEquals("other:shell", r.key());
        assertEquals(6, r.fragments());
        assertEquals(3, r.addUses());
        assertEquals(10, r.maxUses());
        assertEquals(1, warnings.size(), warnings.toString());
    }
}
