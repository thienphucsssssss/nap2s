package vn.twosmc.nap2s.config

class Language : FileConfig("messages") {

    val updateLatest = get("update-latest")
    val updateAvailable = get("update-available")
    val updateAvailableLink = get("update-available-link")
    val errorUnknown = get("error-unknown")
    val errorUnknownCardType = get("error-unknown-card-type")
    val errorUnknownCardPrice = get("error-unknown-card-price")

    val errorSeriTooLong = get("error-seri-too-long")
    val errorPinTooLong = get("error-pin-too-long")

    val cardStatus = get("card-status")

    val cardCharging = getList("card-charging")
    val cardChargedSuccessfully = getList("card-charged-successfully")
    val cardChargedWithExtra = get("card-charged-with-extra")
    val cardChargedWithExtraPlanned = get("card-charged-with-extra-planned")
    val cardChargedWithCardBonus = get("card-charged-with-card-bonus")
    val cardChargedSent = getList("card-charged-sent")
    val cardChargedAnnounce = getList("card-charged-announce")
    val cardChargedFailed = getList("card-charged-failed")
    val cardChargedError = getList("card-charged-error")
    val cardRouteSelected = get("card-route-selected")
    val cardRouteBonus = get("card-route-bonus")
    val cardRouteUnavailable = get("card-route-unavailable")
    val bankingDisabled = get("banking-disabled")
    val bankingInvalidAmount = get("banking-invalid-amount")
    val bankingCooldown = get("banking-cooldown")
    val bankingCreating = get("banking-creating")
    val bankingPendingExists = get("banking-pending-exists")
    val bankingBackendError = get("banking-backend-error")
    val bankingNoPending = get("banking-no-pending")
    val bankingExpired = get("banking-expired")
    val bankingCancelled = get("banking-cancelled")
    val bankingCancelNoOrder = get("banking-cancel-no-order")
    val bankingCustomAmountPrompt = get("banking-custom-amount-prompt")
    val bankingCreated = getList("banking-created")
    val bankingStatus = getList("banking-status")
    val bankingPaid = getList("banking-paid")

    val uiNoAnnouncement = get("ui-no-annoucement")
    val uiNoBankingLocation = get("ui-no-banking-location")

    val inputCancel = get("input-cancel")
    val inputCanceled = get("input-canceled")
    val inputSeri = get("input-seri")
    val inputPin = get("input-pin")
    val inputAnvilSeri = get("input-anvil-seri")
    val inputAnvilPin = get("input-anvil-pin")
    val inputAnvilClick = get("input-anvil-click")

    val logOutPut = get("log-output")
    val transactionIdDetailsOutPut = getList("transaction-id-details-output")
}
