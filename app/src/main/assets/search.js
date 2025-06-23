let allRecipes = [];
let filteredRecipes = [];

document.addEventListener('DOMContentLoaded', function() {
    console.log("Search page loaded");

    Android.loadSavedRecipes();

    document.getElementById('clear-search').addEventListener('click', function() {
        document.getElementById('search-input').value = '';
        searchRecipes();
    });
});

function displaySavedRecipes(recipesJson) {
    console.log("Displaying saved recipes");

    document.getElementById('loading-container').style.display = 'none';

    try {
        allRecipes = JSON.parse(recipesJson);
        filteredRecipes = [...allRecipes];

        updateResultsCount(allRecipes.length);

        renderRecipes(allRecipes);
    } catch (error) {
        console.error("Error parsing recipes JSON:", error);
        showNoResults("Error loading recipes");
    }
}

function searchRecipes() {
    const searchTerm = document.getElementById('search-input').value.trim().toLowerCase();

    if (searchTerm === '') {
        filteredRecipes = [...allRecipes];
        updateResultsCount(filteredRecipes.length);
    } else {
        filteredRecipes = allRecipes.filter(recipe => {
            return recipe.title.toLowerCase().includes(searchTerm) ||
                   (recipe.content && recipe.content.toLowerCase().includes(searchTerm));
        });

        updateResultsCount(filteredRecipes.length, searchTerm);
    }

    renderRecipes(filteredRecipes);
}

function clearSearch() {
    document.getElementById('search-input').value = '';
    searchRecipes();
}

function updateResultsCount(count, searchTerm = '') {
    const resultsCountElement = document.getElementById('results-count');

    if (searchTerm === '') {
        if (count === 0) {
            resultsCountElement.textContent = 'No saved recipes';
        } else {
            resultsCountElement.textContent = `All saved recipes (${count})`;
        }
    } else {
        if (count === 0) {
            resultsCountElement.textContent = `No results for "${searchTerm}"`;
        } else {
            resultsCountElement.textContent = `${count} result${count > 1 ? 's' : ''} for "${searchTerm}"`;
        }
    }
}

function renderRecipes(recipes) {
    const recipesContainer = document.getElementById('recipes-container');

    const loadingContainer = document.getElementById('loading-container');
    recipesContainer.innerHTML = '';
    if (loadingContainer) {
        recipesContainer.appendChild(loadingContainer);
    }

    if (recipes.length === 0) {
        document.getElementById('no-results').style.display = 'flex';
    } else {
        document.getElementById('no-results').style.display = 'none';

        recipes.forEach((recipe, index) => {
            const recipeCard = createRecipeCard(recipe, index);
            recipesContainer.appendChild(recipeCard);
        });
    }
}

function createRecipeCard(recipe, index) {
    const card = document.createElement('div');
    card.className = 'recipe-card';
    card.style.animationDelay = `${index * 0.05}s`;
    card.setAttribute('data-id', recipe.id);
    card.onclick = function() {
        const recipeId = this.getAttribute('data-id');
        console.log("Recipe clicked:", recipeId);
        Android.loadRecipeDetails(parseInt(recipeId));
    };

    let description = '';
    if (recipe.content) {
        const contentElement = document.createElement('div');
        contentElement.innerHTML = recipe.content;
        const paragraphs = contentElement.querySelectorAll('p');
        if (paragraphs.length > 0) {
            description = paragraphs[0].textContent;
        }
    }

    let tags = [];
    if (recipe.content) {
        const lowerContent = recipe.content.toLowerCase();
        const possibleTags = ['vegan', 'vegetarian', 'gluten-free', 'dairy-free', 'low-carb', 'keto', 'paleo'];
        tags = possibleTags.filter(tag => lowerContent.includes(tag));
    }

    card.innerHTML = `
        <img src="${recipe.imageUrl || 'default_recipe.jpg'}" alt="${recipe.title}" class="recipe-image" onerror="this.src='default_recipe.jpg'">
        <div class="recipe-content">
            <h3 class="recipe-title">${recipe.title}</h3>
            ${description ? `<p class="recipe-description">${description}</p>` : ''}
            ${tags.length > 0 ? `
                <div class="recipe-tags">
                    ${tags.map(tag => `<span class="recipe-tag">${tag}</span>`).join('')}
                </div>
            ` : ''}
        </div>
    `;

    return card;
}

function showNoResults(message = "No recipes found") {
    document.getElementById('loading-container').style.display = 'none';
    document.getElementById('no-results').style.display = 'flex';
    document.getElementById('no-results').querySelector('p').textContent = message;
}

function goToHome() {
    window.location.href = "home.html";
}
