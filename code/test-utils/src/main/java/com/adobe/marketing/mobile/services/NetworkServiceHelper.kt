package com.adobe.marketing.mobile.services

open class NetworkServiceHelper: Networking {
    private val delegate: NetworkService = NetworkService()

    override fun connectAsync(request: NetworkRequest?, callback: NetworkCallback?) {
        delegate.connectAsync(request, callback)
    }
}