package dev.snapledger.core.model

/** Whether money left (OUT) or arrived in (IN) the account. */
enum class Direction { IN, OUT }

/** Accounting type; see docs/05-categories-and-rules.md section 1. */
enum class TxnType { EXPENSE, INCOME, REFUND, TRANSFER }

enum class CounterpartyType { MERCHANT, PERSON, UNKNOWN }

/** Why a transaction has its category, in priority order (docs/05 section 3.1). */
enum class ClassificationSource { MANUAL, USER_RULE, BUILTIN_RULE, AI_SUGGESTION, NONE }

enum class TxnOrigin { IMPORT, MANUAL }
