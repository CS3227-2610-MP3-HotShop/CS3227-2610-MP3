---
  layout: default.md
  title: "User Guide"
  pageNav: 3
---

# HotShop User Guide

---

HotShop is a desktop marketplace where you can buy and sell items, exchange
messages, make offers, and arrange meetups. One account supports both buying
and selling.

## Introduction

Welcome to the HotShop User Guide. This guide explains how to get started and
use HotShop's marketplace features.

Begin with [Getting Started](userGuide/GettingStarted.html) for requirements and
launch instructions, including the Intel and Apple Silicon macOS JARs.
Then read [Accounts and Interface](userGuide/AccountsAndInterface.html)
to create an account and find your way around. Use the sidebar to explore each
topic in detail.

## Purpose of this Guide

This guide aims to:

1. Help you [search for items and manage listings](userGuide/SearchAndListings.html).
2. Explain how to [make offers and complete sales](userGuide/OffersAndSales.html),
   [exchange messages](userGuide/Conversations.html), and
   [arrange meetups](userGuide/Meetups.html).
3. Provide a reference for the [Marketplace Rules](userGuide/MarketplaceRules.html),
   including restrictions and important behaviour to know when buying and selling.
4. Define the marketplace terms used throughout, in the
   [Glossary](userGuide/Glossary.html).

## Generating a listing description

In **Create Listing**, enter a **Title** (1–120 characters after trimming) and
choose a **Condition**, then select **Generate Description**. For example,
`Wooden chair` and `Good` are enough; price, category, photos and pickup location
are not needed for generation. Your title and condition are sent to an external
LLM. Your existing description and other listing fields are not sent.

A successful result immediately replaces the entire Description field without
confirmation. Generated text has at most 100 words; review its accuracy and edit
it as needed. It remains unsaved until you fill in the required listing fields
and choose **Save Listing**. Generation is available only when creating a listing.

While generating, progress appears and Generate is disabled. You can still edit,
save manually or leave using the usual unsaved-changes confirmation. Currently,
wait for generation to finish before editing title, condition or description:
a result can replace edits made while you remain on that form. Further protection
for edits and window disposal is still pending.

Failures show feedback beside Generate and preserve your draft. You can retry
deliberately or write your own description; missing provider settings do not
prevent manual saving. One request may run per account, with limits of five
attempts per rolling minute and thirty per rolling hour. A local rate-limit
message gives a retry delay. Provider failures can still count toward these limits.
