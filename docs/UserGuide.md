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

## PostgreSQL setup and persistence

The operator must configure the PostgreSQL connection and run the separate schema
setup command before launching HotShop; see [PostgreSQL setup](developerGuide/AccountServiceAndLocalPersistence.html#postgresql-operator-setup).
An internet or local network connection to that database is required. Marketplace
records persist across application restarts. New installations start empty; old SQLite
files are not imported or reset. Listing and profile photos use private Supabase buckets. Your local data directory retains the
application lock and temporary browser staging. The operator must verify/import existing photos and activate both bucket bindings after schema setup; see [private image storage setup](developerGuide/AccountServiceAndLocalPersistence.html#private-image-storage-setup-and-import).

If startup reports a database setup/connectivity problem, ask the operator to check
connection settings and run compatible provisioning. A folder/lock error means the
local working folder is unavailable or another instance is using it. Startup can also fail when private image storage settings or bindings are missing; the operator must verify configuration and activation. Data is not reset to
recover from either condition. If an operation asks you to retry after a concurrent
change, refresh the relevant record and submit the action again.

JPro browser runtimes have separate login and service state and lease one process-owned data-directory lock. Another independent process remains excluded. Hosted two-browser verification remains a release check. Stored photos persist across application restarts, while reads and writes require network access to Supabase.

## Optional AI Search

On the Search page, the optional **AI Search** button uses 1–1,000 Unicode
code points of text from the existing search field to propose filters. Review the title, category, conditions,
price range, and sort order in the preview, then choose **Apply filters** to
replace the current filter draft and run the ordinary search. Choose **Dismiss**
to discard the preview. Typing, changing filters, or running a manual search
clears a pending proposal. If AI search is not configured or a request fails,
the page shows a fixed status message; ordinary search remains available.

For example, enter **used bicycle under S$200**, choose **AI Search**, and review
the proposed filters before applying them. Ordinary **Search** and Enter do not
call the AI service. Leaving Search or logging out discards the pending proposal.
Images remain local; no Supabase Storage bucket is needed to use AI Search.

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
