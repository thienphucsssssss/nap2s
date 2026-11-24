package vn.twosmc.nap2s.card

class Card(var seri: String, var pin: String, val price: CardPrice, val type: CardType) {
    var logId: Int? = null
    var providerId: String = ""

    override fun toString() = "Card(seri=$seri, pin=$pin, price=$price, type=$type)"
}
