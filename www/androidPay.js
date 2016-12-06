/*global cordova, module*/

module.exports = {
    greet: function (name, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "androidPay", "greet", [name]);
    },

    isReady: function (name, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "androidPay", "greet", [name]);
    }
};
