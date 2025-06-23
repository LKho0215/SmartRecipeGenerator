function signIn() {

    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;
    var message = document.getElementById('message');

    if (email && password) {
        Android.signIn(email, password);
    } else {
        message.textContent = "Please fill in all fields";
        message.style.color = "red";
    }
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

function viewProfile(){
    window.location.href = "profile.html";
}

document.addEventListener('DOMContentLoaded', function() {
    console.log("Page loaded");

    const currentPage = window.location.pathname.split('/').pop();
    console.log("Current page:", currentPage);

    if (currentPage === 'home.html') {
        console.log("Home page loaded");

        if (typeof Android !== 'undefined') {
            console.log("Android object is available");

            console.log("Calling Android.loadSavedRecipes()");
            Android.loadSavedRecipes();
        } else {
            console.log("Android object is NOT available");
        }
    }

    if (currentPage === 'profile.html') {
        console.log("Profile page loaded");

        if (typeof Android !== 'undefined') {
            Android.getUserInfo();
        }
    }
});

function updateUserInfo(name, email) {
    document.getElementById('username').textContent = name;
    document.getElementById('useremail').textContent = email;
}

function editProfile() {
    window.location.href = "edit_profile.html";
}

function logout() {
    Android.logout();
}

function showDeleteConfirm() {
    document.getElementById('delete-modal').style.display = 'flex';
}

function cancelDelete() {
    document.getElementById('delete-modal').style.display = 'none';
}

function confirmDelete() {
    document.getElementById('delete-modal').style.display = 'none';
    console.log("User confirmed deletion");
    Android.deleteAccount();
}

function displaySavedRecipes(recipesJson) {
    console.log("displaySavedRecipes called with data:", recipesJson);

    const recipesSection = document.querySelector('.recipes');
    if (!recipesSection) {
        console.error("Could not find .recipes element");
        return;
    }

    let recipes;
    try {
        recipes = JSON.parse(recipesJson);
        console.log("Parsed recipes:", recipes);
    } catch (e) {
        console.error("Error parsing recipes JSON:", e);
        return;
    }

    const sectionHeader = recipesSection.querySelector('.section-header');

    recipesSection.innerHTML = '';
    if (sectionHeader) {
        recipesSection.appendChild(sectionHeader);
    } else {
        const header = document.createElement('div');
        header.className = 'section-header';
        header.innerHTML = '<h2>Saved Recipes</h2><span class="view-all" onclick="location.href=\'saved_recipes.html\'">View All</span>';
        recipesSection.appendChild(header);
    }

    if (recipes.length === 0) {
        console.log("No recipes found");
        const noRecipes = document.createElement('p');
        noRecipes.className = 'no-recipes';
        noRecipes.textContent = 'No saved recipes yet. Create your first recipe!';
        recipesSection.appendChild(noRecipes);
        return;
    }

    console.log("Creating horizontal recipe container for", recipes.length, "recipes");
    const recipeContainer = document.createElement('div');
    recipeContainer.className = 'recipes-scroll-container';

    recipes.forEach(recipe => {
        console.log("Adding recipe:", recipe.title);
        const recipeCard = document.createElement('div');
        recipeCard.className = 'recipe-scroll-card';
        recipeCard.setAttribute('data-id', recipe.id);

        recipeCard.addEventListener('click', function() {
            const recipeId = this.getAttribute('data-id');
            console.log("Recipe clicked:", recipeId);
            Android.loadRecipeDetails(parseInt(recipeId));
        });

        let imageHtml = '';
        if (recipe.imageUrl && recipe.imageUrl.trim() !== '') {
            imageHtml = `<div class="recipe-image"><img src="${recipe.imageUrl}" alt="${recipe.title}"></div>`;
        } else {
            imageHtml = `<div class="recipe-image no-image"><span>No Image</span></div>`;
        }

        const titleHtml = `<div class="recipe-title">${recipe.title}</div>`;

        recipeCard.innerHTML = imageHtml + titleHtml;
        recipeContainer.appendChild(recipeCard);
    });

    recipesSection.appendChild(recipeContainer);
    console.log("Recipe container added to DOM");
}

function showEditForm(formType) {
    document.querySelectorAll('.edit-form').forEach(form => {
        form.classList.remove('active');
    });

    document.getElementById(formType + '-form').classList.add('active');

    document.querySelectorAll('.error-message').forEach(error => {
        error.textContent = '';
    });

    document.querySelectorAll('input').forEach(input => {
        input.value = '';
    });
}

function cancelEdit() {
    document.querySelectorAll('.edit-form').forEach(form => {
        form.classList.remove('active');
    });
}

function updateName() {
    const newName = document.getElementById('new-name').value.trim();
    const errorElement = document.getElementById('name-error');

    if (!newName) {
        errorElement.textContent = 'Name cannot be empty';
        return;
    }

    Android.updateUserName(newName);
}

function updateEmail() {
    const newEmail = document.getElementById('new-email').value.trim();
    const errorElement = document.getElementById('email-error');

    if (!newEmail) {
        errorElement.textContent = 'Email cannot be empty';
        return;
    }

    if (!isValidEmail(newEmail)) {
        errorElement.textContent = 'Please enter a valid email address';
        return;
    }

    Android.updateUserEmail(newEmail);
}

function updatePassword() {
    const currentPassword = document.getElementById('current-password').value;
    const newPassword = document.getElementById('new-password').value;
    const confirmPassword = document.getElementById('confirm-password').value;
    const errorElement = document.getElementById('password-error');

    if (!currentPassword) {
        errorElement.textContent = 'Current password is required';
        return;
    }

    if (!newPassword) {
        errorElement.textContent = 'New password is required';
        return;
    }

    if (newPassword.length < 6) {
        errorElement.textContent = 'Password must be at least 6 characters';
        return;
    }

    if (newPassword !== confirmPassword) {
        errorElement.textContent = 'New passwords do not match';
        return;
    }

    Android.updateUserPassword(currentPassword, newPassword);
}

function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
}

function handleUpdateSuccess(message) {
    alert(message);
    window.location.href = "profile.html";
}

function handleUpdateError(errorMessage) {
    const activeForm = document.querySelector('.edit-form.active');
    if (activeForm) {
        const errorElement = activeForm.querySelector('.error-message');
        if (errorElement) {
            errorElement.textContent = errorMessage;
        }
    }
}

function showTransition() {
    const transition = document.createElement('div');
    transition.className = 'page-transition';

    const loader = document.createElement('div');
    loader.className = 'loader';
    transition.appendChild(loader);

    const welcomeText = document.createElement('div');
    welcomeText.className = 'welcome-text';
    welcomeText.innerHTML = '<h2>Welcome</h2><p>Preparing your recipes...</p>';
    transition.appendChild(welcomeText);

    document.body.appendChild(transition);
}

function handleLoginSuccess() {
    showTransition();
}

function handleLoginError(errorMessage) {
    const transition = document.querySelector('.page-transition');
    if (transition) {
        transition.classList.add('fade-out');
        setTimeout(() => {
            transition.remove();
        }, 300);
    }

    alert(errorMessage);
}