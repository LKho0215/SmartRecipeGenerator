function signIn() {
    var email = document.getElementById('email').value;
    var password = document.getElementById('password').value;
    var message = document.getElementById('message');

    Android.signIn(email, password);
}

function signUp() {
    var email = document.getElementById('email').value;
    var password = document.getElementById('password').value;
    var message = document.getElementById('message');

    if (email && password) {
        Android.signUp(email, password);
    } else {
        message.textContent = "Please fill in all fields";
        message.style.color = "red";
    }
}

function snapPhoto() {
    Android.snapPhoto();
}