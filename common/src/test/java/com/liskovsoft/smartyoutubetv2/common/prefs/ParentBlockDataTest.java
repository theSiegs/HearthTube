package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.prefs.ParentBlockData.Item;

import org.junit.Test;

import java.util.List;

/** What a parent blocked, as Hearth hands it over (provider contract 8). Parsing its JSON needs a real org.json, so it is tried on a device. */
public class ParentBlockDataTest {
    @Test
    public void aWordIsComparedTrimmedFoldedAndLowerCase() {
        assertEquals("a b", ParentBlockData.foldWord("  A   B "));
        assertNull(ParentBlockData.foldWord("   "));
        assertNull(ParentBlockData.foldWord(null));
    }

    @Test
    public void aItemWithoutALabelIsShownByItsId() {
        assertEquals("abc123", new Item(Item.LOCAL, Item.VIDEO, "abc123", " ").label);
        assertEquals("A title", new Item(Item.LOCAL, Item.VIDEO, "abc123", "A title").label);
    }

    @Test
    public void theSameBlockFromTwoSourcesIsOne() {
        assertTrue(new Item(Item.LOCAL, Item.VIDEO, "abc123", null).sameBlock(new Item(Item.HEARTH, Item.VIDEO, "abc123", null)));
        assertFalse(new Item(Item.LOCAL, Item.VIDEO, "abc123", null).sameBlock(new Item(Item.LOCAL, Item.CHANNEL, "abc123", null)));
    }
}
