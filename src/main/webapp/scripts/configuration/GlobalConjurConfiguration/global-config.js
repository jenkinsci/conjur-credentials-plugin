function validateNumber(evt) {
    const theEvent = evt || window.event;
    let key;

    if (theEvent.type === 'paste') {
        key = event.clipboardData.getData('text/plain');
    } else {
        key = theEvent.keyCode || theEvent.which;
        key = String.fromCharCode(key);
    }
    const regex = /\d|\./;
    if (!regex.test(key)) {
        theEvent.returnValue = false;
        if (theEvent.preventDefault) {
            theEvent.preventDefault();
        }
    }
}

document.getElementById('listAuthenticator').addEventListener('change', changeAuthenticationOption);

function getIdentityFormatToken() {
    const listAuthenticator = document.getElementById('listAuthenticator');
    const selectAuthenticator = listAuthenticator.dataset.authenticator;
    let selectIdentityFormatTokenValue = listAuthenticator.dataset.token;

    const selectedIdentityFormatFiledToken = document.getElementById('listIdentityFormatFieldsFromToken');
    selectIdentityFormatTokenValue = selectIdentityFormatTokenValue || 'jenkins_full_name';
    selectedIdentityFormatFiledToken.value = selectIdentityFormatTokenValue;

    for (let j = 0; j < listAuthenticator.length; j++) {
        if (listAuthenticator.options[j].value === selectAuthenticator) {
            listAuthenticator.options[j].selected = true;
        }
    }
}

getIdentityFormatToken();

function changeAuthenticationOption() {
    const listAuthenticator = document.getElementById('listAuthenticator');
    const optionText = listAuthenticator.options[listAuthenticator.selectedIndex].text;
    const conjurGlobalJWTSection = document.getElementById('conjurGlobalJWTSection');
    const conjurLocalAPIKeyCredentials = document.getElementById('conjurLocalAPIKeyCredentials');
    const conjurLocalTokenClaims = document.getElementById('conjurLocalTokenClaims');
    const jwtBtn = document.getElementById('jwtValidateButton');

    if (optionText === 'APIKey') {
        conjurGlobalJWTSection.style.display = 'none';
        conjurLocalAPIKeyCredentials.style.display = 'block';
        conjurLocalTokenClaims.style.display = 'none';
        jwtBtn.style.display = 'none';
    } else if (optionText === 'JWT') {
        conjurGlobalJWTSection.style.display = 'block';
        conjurLocalAPIKeyCredentials.style.display = 'none';
        conjurLocalTokenClaims.style.display = 'block';
        jwtBtn.style.display = 'none';
    }
}

changeAuthenticationOption();
