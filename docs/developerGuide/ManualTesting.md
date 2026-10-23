---
  layout: default.md
  title: "Manual testing"
  pageNav: 3
---

## Appendix: Instructions for manual testing

These steps test the listing, offer, sale, meetup, and chat features with two
accounts. They complement the [User Guide](../UserGuide.html), which explains each
screen; testing of accounts, profiles, and search is covered separately.

This walkthrough changes accounts between steps because the sale depends on
each participant's previous action. JPro browser sessions have independent
login state, so account and image-upload checks can also be run in two browser
sessions without logging either one out.

### Preparing isolated local test data

Use a disposable local PostgreSQL database and a separate HotShop data folder;
never point manual tests at hosted or demonstration data. Configure
`HOTSHOP_DB_URL`, `HOTSHOP_DB_USER`, and `HOTSHOP_DB_PASSWORD` for that database
and provision its schema using the developer setup instructions. Build the JAR,
then start HotShop on the test folder (PowerShell):

```powershell
.\gradlew.bat shadowJar
java "-Dhotshop.dataDir=$env:TEMP\hotshop-test" -jar release\HotShop.jar
```

The data folder holds the process lock and temporary browser staging; deleting
it does not reset PostgreSQL. Reset the disposable database separately when a
clean test run is needed. For independent browser sessions in one process, use
the same data folder; each session still has separate login and service state.

Register three accounts (see the User Guide's account section). Carol is only
needed for the refusal checks:

| Username | Display name | Password |
| --- | --- | --- |
| `alice` | `Alice` | `Sample1!` |
| `bobby` | `Bob` | `Sample1!` |
| `carol` | `Carol` | `Sample1!` |

### Browser sessions and private image storage

Run this acceptance check only with a disposable local PostgreSQL database and
private test Storage buckets configured as described in [private image storage
setup](AccountServiceAndLocalPersistence.html#private-image-storage-setup-and-import).
Use the repository's [local JPro workflow](DevelopmentWorkflow.html#jpro-and-docker-packaging)
or its Docker/JPro package workflow; the JAR command above is for desktop manual
testing. Keep provider settings outside the image and open the application at
`http://localhost:8080` in two separate temporary Chrome contexts.

1. Create two test accounts and log into one in each browser context. Verify that
   logging out of one session leaves the other session logged in.
2. In the first context, open **My Profile** and click **Replace Image**. Confirm
   the chooser opens on the first click, cancel it, then click again and select a
   small valid JPEG or PNG. Verify that the selected photo appears. On **Create
   Listing**, repeat the first-click, cancel, retry and selection steps with **Add
   Photo**, then save and verify the listing photo appears.
3. Replace the application container without mounting its previous image volume,
   while preserving the disposable local PostgreSQL database and private test
   Storage. Log in again and verify the profile and listing photos still load.
4. In the second context, open the first account's public profile and listing.
   Verify both photos are readable while the second account remains independent.
5. Remove the first account's profile photo and delete its test listing. Verify
   that only those test objects are removed from the private test buckets.

Never point this check at a hosted marketplace database or delete objects beyond
the test accounts' own objects in the private test buckets.

### Walkthrough: from listing to completed sale

1. **As Alice, create a listing.** My Listings, then **Create Listing**: title
   `Study desk`, description `Wooden desk with one drawer`, category Furniture,
   condition Good, price `50.00`, pickup location `Library entrance`. Choose
   **Save Listing**.
   Expected: the card appears in My Listings as Available with 0 pending offers.
2. **As Bob, message the seller.** Search, open **Study desk**, choose **Chat
   with seller**. The page shows "No messages yet." and a "No offer yet" bar.
   Type `Is it still available?` and choose **Send**.
   Expected: the message appears; Conversations lists the conversation with a
   Buying badge.
3. **As Bob, make an offer with a message.** In the conversation's bar choose
   **Make Offer**, enter `40.00` and the message `Can pick up tonight`, and
   choose **Submit Offer**.
   Expected: the bar shows "Offer of S$40.00 · Pending" with **Withdraw Offer**,
   and the message appears in the conversation.
4. **As Alice, see the unread items and accept.** The sidebar shows
   **Conversations (3)**: Bob's two messages plus the new offer. Open the
   conversation, choose **Accept Offer**, and confirm.
   Expected: Sale Details opens; the listing is Reserved; Conversations now
   shows no unread count for it.
5. **As Alice, offer a meetup time.** On Sale Details choose **Open Chat**. The
   bar says "No meetup times offered yet". Choose **Offer Time**; the dialog
   starts at tomorrow 12:00 for 30 minutes at `Library entrance`. Choose **Offer
   Time** again to save.
   Expected: the bar shows "1 time offered" with **View Times** and **Offer
   Time**.
6. **As Bob, book the time.** Open the conversation, choose **Choose Time**,
   then **Book**.
   Expected: the bar shows "Meetup: <tomorrow's date>, 12:00 to 12:30 · Library
   entrance" with **Propose Move**, **Cancel Meetup**, and **View Sale**.
7. **As Bob, propose a move.** Choose **Propose Move**, change the start time to
   `14:00`, and choose **Propose Move**.
   Expected: the bar says "You proposed moving the meetup to …" with **Withdraw
   Proposal**; Propose Move and Cancel Meetup are hidden while it is pending.
8. **As Alice, accept the move.** Open the conversation and choose **Accept
   Move**.
   Expected: the meetup now runs 14:00 to 14:30. My Listings shows the date,
   time, and place on the reserved card.
9. **Complete the sale.** As Alice, open My Sales, open the sale, choose
   **Confirm Completion**, and confirm. As Bob, do the same from My Purchases.
   Expected: after Bob confirms, the sale is completed, the listing is Sold, the
   conversation's bar shows "Sale completed · Met on …", and the send box is
   disabled because the listing is sold.

### Checks for refusals and edge cases

Start each from a new listing by Alice unless stated.

| Check | Steps | Expected |
| --- | --- | --- |
| One pending offer | As Bob, make an offer, then open the listing again. | Make Offer is replaced by your pending offer and **Withdraw Offer**. |
| Offer on a reserved listing | After Alice accepts Bob's offer, as Carol, open the listing. | Make Offer is disabled with "Only available listings can receive offers." Carol can still choose **Chat with seller**. |
| Other offers rejected on accept | As Bob and Carol, each make an offer; as Alice, accept Bob's. | Carol's offer shows as Rejected, and Carol's conversation shows an unread item. |
| Cancel before confirming | As Bob, offer `40.00`; as Alice, accept it. Then, as either user, open Sale Details and choose **Cancel Sale**, then confirm. | The sale is Cancelled and the listing is Available again. Bob's bar shows "Offer of S$40.00 · Accepted · Sale Cancelled" with **View Sale** and **Make Offer**. |
| Cancellation needs agreement | In an active sale, as Alice, choose **Confirm Completion**; then **Request Cancellation**. As Bob, open the sale. | Confirm Completion is disabled for Bob; **Accept Cancellation** and **Reject Cancellation** are shown. Accepting cancels the sale; rejecting keeps it active. |
| At most three offered times | In an active sale with no booked meetup, as Alice, offer three different times. | After the third, **Offer Time** is no longer shown. |
| Meetups up to 60 days ahead | In the Offer Time dialog, open the date picker. | Dates before today and after the 60th day from today are disabled; any start time on day 60 is accepted. |
| No double bookings | With a booked meetup between Alice and Bob (walkthrough steps 1 to 8, before completing the sale), create a second listing and sale between them; as Alice, offer the same time as the booked meetup. | Refused: "You already have a meetup from … to …. Choose a different time." |
| Cancel a meetup | With a booked meetup, choose **Cancel Meetup** and confirm. | The sale stays active and the bar asks the seller to offer times again. |
| Messages are limited | In any open conversation, paste a message over 1,000 characters and choose **Send**. | The counter below Send shows, for example, "1234 / 1,000". Sending is refused with "Messages can be at most 1,000 characters, but this one has 1,234." and the draft is kept. |
| Delete a listing with enquiries | As Bob, message Alice about a new listing without offering. As Alice, open the listing and choose **Delete Listing**. | The confirmation adds "1 conversation about this listing will also be deleted, for you and the buyers." After confirming, Bob no longer has the conversation. |
| Delete is refused after an offer | As Bob, make and then withdraw an offer. As Alice, open the listing. | **Delete Listing** is disabled; archive the listing instead. |
