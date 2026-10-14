package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KeywordFilterTest {
    @Test
    public void theFirstAdultAndEveryKidKeepThePlainList() {
        assertEquals("words", KeywordFilter.wordsKey(null));
        assertEquals("words", KeywordFilter.wordsKey("user:0"));
        assertEquals("words", KeywordFilter.wordsKey("user:11"));
    }

    @Test
    public void otherAdultsInTheOwnersUserHaveAListOfTheirOwn() {
        assertEquals("words:user:0:sam", KeywordFilter.wordsKey("user:0:sam"));
        assertEquals("words:user:0:alex-2", KeywordFilter.wordsKey("user:0:alex-2"));
    }
}
