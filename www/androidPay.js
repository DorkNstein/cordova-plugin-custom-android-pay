/*global cordova, module*/

module.exports = {
    buy: function (amount, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "androidPay", "buy", [amount]);
    },

    isReady: function (amount, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "androidPay", "isReady", [amount]);
    }
};
