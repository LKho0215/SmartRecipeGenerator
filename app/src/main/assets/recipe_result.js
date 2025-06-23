document.addEventListener('DOMContentLoaded', function() {
    console.log("Recipe result page loaded");

    const urlParams = new URLSearchParams(window.location.search);
    const isSaved = urlParams.get('saved') === 'true';
    const recipeId = urlParams.get('id');

    console.log("URL params check - isSaved:", isSaved, "recipeId:", recipeId);

    if (!isSaved) {
        console.log("This is a new recipe - clearing saved recipe data");

        document.body.removeAttribute('data-saved-recipe-id');

        const saveButton = document.querySelector('.save-btn');
        if (saveButton) {
            saveButton.style.display = 'flex';
        }

        const deleteButton = document.getElementById('delete-recipe-btn');
        if (deleteButton) {
            deleteButton.style.display = 'none';
        }

        const shareButton = document.querySelector('.share-btn');
        if (shareButton) {
            shareButton.style.display = 'none';
        }

        const savedBadge = document.querySelector('.saved-badge');
        if (savedBadge) {
            savedBadge.remove();
        }

        console.log("Calling Android.getGeneratedRecipe()");
        Android.getGeneratedRecipe();
    } else {
        console.log("This is a saved recipe with ID:", recipeId);

        const shareButton = document.querySelector('.share-btn');
        if (shareButton) {
            shareButton.style.display = 'flex';
        }

        if (recipeId) {
            document.body.dataset.savedRecipeId = recipeId;

            console.log("Calling Android.getSavedRecipe() with ID:", recipeId);
            Android.getSavedRecipe(recipeId);

            markAsSavedRecipe(recipeId);
        } else {
            console.error("No recipe ID provided in URL");
            showError("Recipe not found");
        }
    }
});

function displayRecipe(recipeContent) {
    document.getElementById('recipe-content').innerHTML = recipeContent;

    const elements = document.querySelectorAll('.recipe-content > *');
    elements.forEach((element, index) => {
        element.style.opacity = '0';
        element.style.transform = 'translateY(20px)';
        element.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
        element.style.transitionDelay = `${index * 0.1}s`;

        setTimeout(() => {
            element.style.opacity = '1';
            element.style.transform = 'translateY(0)';
        }, 100);
    });
}

function displayRecipeImage(imageUrl) {
    const imageContainer = document.getElementById('recipe-image-container');
    const recipeImage = document.getElementById('recipe-image');

    recipeImage.src = imageUrl;
    imageContainer.style.display = 'block';
}

function saveRecipe() {
    const recipeContent = document.getElementById('recipe-content').innerHTML;
    const recipeTitle = document.querySelector('#recipe-content h1')?.textContent || 'Untitled Recipe';

    let imageUrl = '';
    const recipeImage = document.getElementById('recipe-image');
    if (recipeImage && recipeImage.src) {
        imageUrl = recipeImage.src;
    }

    const saveButton = document.querySelector('.save-btn');
    const originalText = saveButton.innerHTML;
    saveButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
    saveButton.disabled = true;

    setTimeout(() => {
        Android.saveRecipe(recipeTitle, recipeContent, imageUrl);
    }, 500);
}

function shareRecipe() {
    const recipeContent = document.getElementById('recipe-content').innerHTML;
    const recipeTitle = document.querySelector('#recipe-content h1')?.textContent || 'My Recipe';

    const shareButton = document.querySelector('.share-btn');
    const originalText = shareButton.innerHTML;
    shareButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Sharing...';

    setTimeout(() => {
        Android.shareRecipe(recipeTitle, recipeContent);
        shareButton.innerHTML = originalText;
    }, 500);
}

function goToHome() {
    window.location.href = "home.html";
}

function markAsSavedRecipe(recipeId) {
    document.body.dataset.savedRecipeId = recipeId;

    const saveButton = document.querySelector('.save-btn');
    if (saveButton) {
        saveButton.style.display = 'none';
    }

    const deleteButton = document.getElementById('delete-recipe-btn');
    if (deleteButton) {
        deleteButton.style.display = 'flex';
    }

    const recipeContainer = document.querySelector('.recipe-result-container');

    if (!document.querySelector('.saved-badge')) {
        const savedBadge = document.createElement('div');
        savedBadge.className = 'saved-badge';
        savedBadge.textContent = 'Saved Recipe';
        recipeContainer.appendChild(savedBadge);
    }
}

function confirmDeleteRecipe() {
    document.getElementById('delete-confirm-modal').style.display = 'flex';
}

function cancelDelete() {
    document.getElementById('delete-confirm-modal').style.display = 'none';
}

function deleteRecipe() {
    const recipeId = document.body.dataset.savedRecipeId;
    if (recipeId) {
        const deleteButton = document.querySelector('.modal-confirm');
        const originalText = deleteButton.innerHTML;
        deleteButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Deleting...';
        deleteButton.disabled = true;

        setTimeout(() => {
            Android.deleteRecipe(parseInt(recipeId));
            document.getElementById('delete-confirm-modal').style.display = 'none';
        }, 500);
    } else {
        console.error("No recipe ID found for deletion");
        document.getElementById('delete-confirm-modal').style.display = 'none';
    }
}

function showError(message) {
    document.getElementById('recipe-content').innerHTML = `
        <div class="error-message">
            <i class="fas fa-exclamation-circle"></i>
            <p>${message}</p>
        </div>
    `;
}