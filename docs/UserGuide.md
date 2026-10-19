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
setup command before launching HotShop; see [Developer Guide](DeveloperGuide.html#postgresql-operator-setup).
An internet or local network connection to that database is required. Marketplace
records persist across application restarts. New installations start empty; old SQLite
files are not imported or reset. Your local data directory holds images and the
application lock. Keep it available with the same database when restarting.

If startup reports a database setup/connectivity problem, ask the operator to check
connection settings and run compatible provisioning. A folder/lock error means the
image folder is unavailable or another instance is using it. Data is not reset to
recover from either condition. If an operation asks you to retry after a concurrent
change, refresh the relevant record and submit the action again.

Hosted JPro multi-user sessions and Supabase image storage remain separate integration
work; this database conversion retains the current single-runtime directory lock.

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
