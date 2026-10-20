package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.utils.ProfileFollowRule.Decision;
import com.liskovsoft.smartyoutubetv2.common.utils.ProfileFollowRule.Kind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProfileFollowRuleTest {
    private static final Account ALEX = account("Alex Rivera", "alex@example.com");
    private static final Account SAM = account("Sam Rivera", "sam@example.com");
    private static final Account JORDAN = account("Jordan Smith", "jordan@example.com");

    private static Account account(String name, String email) {
        return new Account() {
            @Override public int getId() { return 0; }
            @Override public String getName() { return name; }
            @Override public String getEmail() { return email; }
            @Override public String getAvatarImageUrl() { return null; }
            @Override public boolean isSelected() { return false; }
            @Override public boolean isEmpty() { return false; }
        };
    }

    private static List<Account> list(Account... accounts) {
        return Arrays.asList(accounts);
    }

    /** A kid's profile: decide(oneAccountProfile = true, ...), whatever Hearth says */
    private static Decision kid(List<Account> accounts, Account selected) {
        return ProfileFollowRule.decide(true, true, "user:10", "Sam", null, accounts, selected, false, null);
    }

    /** The owner's user: a profile "Alex" (key user:11) with the given link and what others are linked to */
    private static Decision owner(Account linked, List<Account> accounts, Account selected, boolean deepLink, List<Account> elsewhere) {
        return ProfileFollowRule.decide(false, true, "user:11", "Alex", linked, accounts, selected, deepLink, elsewhere);
    }

    // A kid's profile

    @Test
    public void aKidWithNoAccountIsLeftToTheWelcome() {
        assertEquals(Kind.STANDARD, kid(Collections.<Account>emptyList(), null).kind);
        assertEquals(Kind.STANDARD, kid(null, null).kind);
    }

    @Test
    public void aKidWithTheOneAccountGetsItWithoutAskingOrLinking() {
        Decision decision = kid(list(SAM), null);

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(SAM, decision.account);
        assertFalse(decision.link);
        assertFalse(decision.guessed);
        assertFalse(decision.deselect);
    }

    @Test
    public void aKidWithTheOneAccountSelectedKeepsIt() {
        Decision decision = kid(list(SAM), SAM);

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(SAM, decision.account);
        assertFalse(decision.link);
    }

    @Test
    public void aKidWithTwoAccountsNeverGetsAChooserAndKeepsTheSelectedOne() {
        Decision decision = kid(list(SAM, ALEX), ALEX);

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(ALEX, decision.account); // a parent chose it in the Accounts page
        assertFalse(decision.link);
    }

    @Test
    public void aKidWithTwoAccountsAndNoneSelectedGetsTheFirst() {
        Decision decision = kid(list(SAM, ALEX), null);

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(SAM, decision.account);
        assertFalse(decision.link);
    }

    @Test
    public void aKidWhoseSelectedAccountIsGoneGetsTheFirst() {
        assertSame(SAM, kid(list(SAM, JORDAN), ALEX).account);
    }

    @Test
    public void aKidIsNeverAskedNorLinkedWhateverHearthOrALinkSays() {
        // Hearth names a profile, a link from an older version exists, a link opened the app, following is off
        Decision decision = ProfileFollowRule.decide(true, false, "user:10", "Sam", ALEX, list(SAM, ALEX), SAM, true, list(ALEX));

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(SAM, decision.account);
        assertFalse(decision.link);
        assertFalse(decision.deselect);
    }

    @Test
    public void aKidWithoutHearthIsTreatedTheSame() {
        Decision decision = ProfileFollowRule.decide(true, true, null, null, null, list(SAM), null, false, null);

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(SAM, decision.account);
    }

    // The owner's user

    @Test
    public void ownerWithNobodySignedInIsLeftToTheWelcome() {
        assertEquals(Kind.STANDARD, owner(null, Collections.<Account>emptyList(), null, false, null).kind);
        assertEquals(Kind.STANDARD, owner(null, null, null, false, null).kind);
    }

    @Test
    public void ownerWithALinkUsesTheLinkedAccount() {
        Decision decision = owner(ALEX, list(SAM, ALEX), SAM, false, list(SAM));

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(ALEX, decision.account);
        assertFalse(decision.link); // already saved
        assertFalse(decision.guessed);
    }

    @Test
    public void aLinkAlsoWinsOnADeepLink() {
        Decision decision = owner(ALEX, list(SAM, ALEX), SAM, true, null);

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(ALEX, decision.account);
    }

    @Test
    public void withoutALinkAUniqueFirstNameMatchIsLinkedAndSaid() {
        Decision decision = owner(null, list(SAM, ALEX), SAM, false, null);

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(ALEX, decision.account);
        assertTrue(decision.link);
        assertTrue(decision.guessed);
    }

    @Test
    public void withoutALinkAnAmbiguousNameAsks() {
        Account otherAlex = account("Alex Smith", "alex.smith@example.com");
        Decision decision = owner(null, list(ALEX, otherAlex), null, false, null);

        assertEquals(Kind.ASK, decision.kind);
        assertFalse(decision.deselect); // nothing selected, nothing to put away
    }

    @Test
    public void withoutALinkAndNoMatchAsksAndPutsTheSelectedAccountAway() {
        Decision decision = owner(null, list(SAM, JORDAN), SAM, false, null);

        assertEquals(Kind.ASK, decision.kind);
        assertTrue(decision.deselect); // one login per profile: Sam's account isn't Alex's
        assertNull(decision.account);
        assertFalse(decision.link);
    }

    @Test
    public void aLinkToARemovedAccountIsNoLinkAtAll() {
        // getLink gives null for an account that is gone: the profile is guessed again, or asked
        assertEquals(Kind.USE_ACCOUNT, owner(null, list(SAM, ALEX), null, false, null).kind);
        assertEquals(Kind.ASK, owner(null, list(SAM), null, false, null).kind);
    }

    @Test
    public void aDeepLinkWithoutALinkNeverAsksNorDeselects() {
        Decision decision = owner(null, list(SAM, JORDAN), SAM, true, null);

        assertEquals(Kind.STANDARD, decision.kind); // plays first, with the account selected now
        assertFalse(decision.deselect);
    }

    @Test
    public void aGuessSkipsAnAccountAnotherProfileIsLinkedTo() {
        // Alex's account is another profile's: this one isn't linked to it by name, it asks
        Decision decision = owner(null, list(SAM, ALEX), null, false, list(ALEX));

        assertEquals(Kind.ASK, decision.kind);
    }

    @Test
    public void aGuessFindsTheOnlyFreeOneOfTwoSameNamedAccounts() {
        Account otherAlex = account("Alex Smith", "alex.smith@example.com");
        Decision decision = owner(null, list(ALEX, otherAlex), null, false, list(ALEX));

        assertEquals(Kind.USE_ACCOUNT, decision.kind);
        assertSame(otherAlex, decision.account);
    }

    @Test
    public void ownerWithFollowingOffOrWithoutHearthKeepsTheUsualLaunch() {
        assertEquals(Kind.STANDARD, ProfileFollowRule.decide(false, false, "user:11", "Alex", ALEX, list(SAM, ALEX), SAM, false, null).kind);
        assertEquals(Kind.STANDARD, ProfileFollowRule.decide(false, true, null, null, null, list(SAM, ALEX), SAM, false, null).kind);
    }

    @Test
    public void twoAdultsOnTheSharedCopyEachGetTheirOwn() {
        List<Account> accounts = list(ALEX, SAM);

        // Alex's profile is linked to Alex's account, Sam's to Sam's: nobody gets the other's
        assertSame(ALEX, ProfileFollowRule.decide(false, true, "user:11", "Alex", ALEX, accounts, SAM, false, list(SAM)).account);
        assertSame(SAM, ProfileFollowRule.decide(false, true, "user:12", "Sam", SAM, accounts, ALEX, false, list(ALEX)).account);
    }

    @Test
    public void twoAdultsWithoutLinksAreGuessedByTheirFirstNames() {
        List<Account> accounts = list(ALEX, SAM);

        Decision alex = ProfileFollowRule.decide(false, true, "user:11", "Alex", null, accounts, SAM, false, null);
        // Alex's profile took Alex's account; Sam's opens next, with it as another profile's
        Decision sam = ProfileFollowRule.decide(false, true, "user:12", "Sam", null, accounts, ALEX, false, list(ALEX));

        assertSame(ALEX, alex.account);
        assertTrue(alex.link);
        assertSame(SAM, sam.account);
        assertTrue(sam.link);
    }

    @Test
    public void aThirdAdultWithNoMatchIsAskedAndOnlyOffersTheUnlinkedAccounts() {
        List<Account> accounts = list(ALEX, SAM, JORDAN);
        List<Account> elsewhere = list(ALEX, SAM);

        Decision decision = ProfileFollowRule.decide(false, true, "user:13", "Kim", null, accounts, ALEX, false, elsewhere);

        assertEquals(Kind.ASK, decision.kind);
        assertTrue(decision.deselect);
        assertEquals(list(JORDAN), ProfileFollowRule.offered(null, accounts, elsewhere));
    }

    // What the picker offers

    @Test
    public void aLinkedProfileOffersItsAccountAndThatAccountsChannels() {
        Account channel = account("Alex's Workshop", "ALEX@example.com");
        List<Account> accounts = list(ALEX, SAM, channel);

        assertEquals(list(ALEX, channel), ProfileFollowRule.offered(ALEX, accounts, list(SAM)));
    }

    @Test
    public void anUnlinkedProfileOffersNothingWhenEveryAccountIsAnotherProfilesAndTheWelcomeSignsIn() {
        assertTrue(ProfileFollowRule.offered(null, list(ALEX, SAM), list(ALEX, SAM)).isEmpty());
    }

    // The same Google account

    @Test
    public void aChannelOfTheSameGoogleAccountIsTheSamePerson() {
        assertTrue(ProfileFollowRule.isSamePerson(account("Alex Rivera", "alex@example.com"),
                account("Alex's Workshop", "ALEX@example.com")));
    }

    @Test
    public void anotherGoogleAccountIsNot() {
        assertFalse(ProfileFollowRule.isSamePerson(account("Alex Rivera", "alex@example.com"),
                account("Sam Rivera", "sam@example.com")));
    }

    @Test
    public void withoutEmailsTheNameDecides() {
        assertTrue(ProfileFollowRule.isSamePerson(account("Alex", null), account("Alex", "alex@example.com")));
        assertFalse(ProfileFollowRule.isSamePerson(account("Alex", null), account("Sam", null)));
        assertFalse(ProfileFollowRule.isSamePerson(null, account("Sam", null)));
    }
}
