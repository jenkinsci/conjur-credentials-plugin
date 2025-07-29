function validateNumber(evt) {
    var theEvent = evt || window.event;
    var key;

  if (theEvent.type === "paste") {
        key = event.clipboardData.getData('text/plain');
    } else {
        key = theEvent.keyCode || theEvent.which;
        key = String.fromCharCode(key);
    }
    var regex = /[0-9]|\./;
    if (!regex.test(key)) {
        theEvent.returnValue = false;
        if (theEvent.preventDefault) theEvent.preventDefault();
    }
}

document.getElementById('listAuthenticator').addEventListener('change', changeAuthenticationOption);

function getIdentityFormatToken() {
    var listAuthenticator = document.getElementById('listAuthenticator');
  var selectAuthenticator = listAuthenticator.dataset.authenticator;
    var selectIdentityFormatTokenValue = listAuthenticator.dataset.token;

    var selectedIdentityFormatFiledToken = document.getElementById("listIdentityFormatFieldsFromToken");
    selectIdentityFormatTokenValue = selectIdentityFormatTokenValue ? selectIdentityFormatTokenValue : 'jenkins_full_name';
    selectedIdentityFormatFiledToken.value = selectIdentityFormatTokenValue;


    for (var j = 0; j < listAuthenticator.length; j++) {
        if (listAuthenticator.options[j].value == selectAuthenticator) {
            listAuthenticator.options[j].selected = true;
        }
    }
}

getIdentityFormatToken();

function changeAuthenticationOption() {
    var listAuthenticator = document.getElementById('listAuthenticator');
    var optionText = listAuthenticator.options[listAuthenticator.selectedIndex].text;
    var conjurGlobalJWTSection = document.getElementById('conjurGlobalJWTSection');
    var conjurLocalAPIKeyCredentials = document.getElementById('conjurLocalAPIKeyCredentials');
    var conjurLocalTokenClaims = document.getElementById('conjurLocalTokenClaims');
    var jwtBtn = document.getElementById('jwtValidateButton');

    if (optionText === 'APIKey') {
        try {
            conjurGlobalJWTSection.style.display = "none";
            conjurLocalAPIKeyCredentials.style.display = "block";
            conjurLocalTokenClaims.style.display = "none";
            jwtBtn.style.display = "none";
        } catch (err) {
        }
    } else if (optionText === 'JWT') {
        conjurGlobalJWTSection.style.display = "block";
        conjurLocalAPIKeyCredentials.style.display = "none";
        conjurLocalTokenClaims.style.display = "block";
        jwtBtn.style.display = "none";
    }
}

changeAuthenticationOption();