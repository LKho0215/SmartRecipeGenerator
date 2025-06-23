// 全局變量
let selectedTypes = [];
let selectedIngredients = [];

document.addEventListener('DOMContentLoaded', function() {
    Android.getPantryItemsForRecipe();
});

function selectType(type) {
    const typeElement = event.currentTarget;

    if (typeElement.classList.contains('selected')) {
        typeElement.classList.remove('selected');
        selectedTypes = selectedTypes.filter(t => t !== type);
    } else {
        typeElement.classList.add('selected');
        if (!selectedTypes.includes(type)) {
            selectedTypes.push(type);
        }
    }

    updateSelectedTypes();
}

function addCustomType() {
    const customTypeInput = document.getElementById('custom-type');
    const customType = customTypeInput.value.trim();

    if (customType && !selectedTypes.includes(customType)) {
        selectedTypes.push(customType);
        customTypeInput.value = '';
        updateSelectedTypes();
    }
}

function updateSelectedTypes() {
    const selectedTypesContainer = document.getElementById('selected-types');
    selectedTypesContainer.innerHTML = '';

    if (selectedTypes.length === 0) {
        return;
    }

    selectedTypes.forEach(type => {
        const typeElement = document.createElement('div');
        typeElement.className = 'selected-type';
        typeElement.innerHTML = `
            ${type}
            <span class="remove" onclick="removeType('${type}')">&times;</span>
        `;
        selectedTypesContainer.appendChild(typeElement);
    });
}

function removeType(type) {
    selectedTypes = selectedTypes.filter(t => t !== type);

    document.querySelectorAll('.type-option').forEach(option => {
        if (option.textContent.trim() === type) {
            option.classList.remove('selected');
        }
    });

    updateSelectedTypes();
}

function selectIngredient(id, name) {
    const ingredientElement = event.currentTarget;

    if (ingredientElement.classList.contains('selected')) {
        ingredientElement.classList.remove('selected');
        selectedIngredients = selectedIngredients.filter(i => i.id !== id);
    } else {
        ingredientElement.classList.add('selected');
        if (!selectedIngredients.some(i => i.id === id)) {
            selectedIngredients.push({ id, name });
        }
    }

    updateSelectedIngredients();
}

function updateSelectedIngredients() {
    const ingredientsList = document.getElementById('ingredients-list');
    const emptySelection = document.querySelector('.empty-selection');

    ingredientsList.innerHTML = '';

    if (selectedIngredients.length === 0) {
        ingredientsList.appendChild(emptySelection);
        return;
    }

    selectedIngredients.forEach(ingredient => {
        const ingredientElement = document.createElement('div');
        ingredientElement.className = 'selected-ingredient';
        ingredientElement.innerHTML = `
            ${ingredient.name}
            <span class="remove" onclick="removeIngredient(${ingredient.id})">&times;</span>
        `;
        ingredientsList.appendChild(ingredientElement);
    });
}

function removeIngredient(id) {
    selectedIngredients = selectedIngredients.filter(i => i.id !== id);

    document.querySelectorAll('.pantry-item').forEach(item => {
        if (parseInt(item.dataset.id) === id) {
            item.classList.remove('selected');
        }
    });

    updateSelectedIngredients();
}

function updatePantryItemsForRecipe(itemsJson) {
    const pantryItemsContainer = document.getElementById('pantry-items');
    pantryItemsContainer.innerHTML = '';

    const items = JSON.parse(itemsJson);

    if (items.length === 0) {
        pantryItemsContainer.innerHTML = '<div class="empty-pantry">Your pantry is empty. Add ingredients in the Pantry page.</div>';
        return;
    }

    items.forEach(item => {
        const itemElement = document.createElement('div');
        itemElement.className = 'pantry-item';
        itemElement.dataset.id = item.id;
        itemElement.textContent = item.name;
        itemElement.onclick = function() { selectIngredient(item.id, item.name); };
        pantryItemsContainer.appendChild(itemElement);
    });
}

function nextStep(currentStep) {
    if (currentStep === 1 && selectedTypes.length === 0) {
        alert('Please select at least one recipe type');
        return;
    }

    if (currentStep === 2 && selectedIngredients.length === 0) {
        alert('Please select at least one ingredient');
        return;
    }

    document.getElementById(`step-${currentStep}-content`).classList.remove('active');
    document.getElementById(`step-${currentStep+1}-content`).classList.add('active');

    document.getElementById(`step-${currentStep}`).classList.remove('active');
    document.getElementById(`step-${currentStep+1}`).classList.add('active');

    if (currentStep === 2) {
        updateRecipeSummary();
    }
}

function prevStep(currentStep) {
    document.getElementById(`step-${currentStep}-content`).classList.remove('active');
    document.getElementById(`step-${currentStep-1}-content`).classList.add('active');

    document.getElementById(`step-${currentStep}`).classList.remove('active');
    document.getElementById(`step-${currentStep-1}`).classList.add('active');
}

function updateRecipeSummary() {
    const summaryTypes = document.getElementById('summary-types');
    const summaryIngredients = document.getElementById('summary-ingredients');

    summaryTypes.innerHTML = '';
    if (selectedTypes.length > 0) {
        selectedTypes.forEach(type => {
            const typeElement = document.createElement('div');
            typeElement.className = 'summary-item';
            typeElement.textContent = type;
            summaryTypes.appendChild(typeElement);
        });
    } else {
        summaryTypes.innerHTML = '<div class="empty-summary">No recipe types selected</div>';
    }
    summaryIngredients.innerHTML = '';
    if (selectedIngredients.length > 0) {
        selectedIngredients.forEach(ingredient => {
            const ingredientElement = document.createElement('div');
            ingredientElement.className = 'summary-item';
            ingredientElement.textContent = ingredient.name;
            summaryIngredients.appendChild(ingredientElement);
        });
    } else {
        summaryIngredients.innerHTML = '<div class="empty-summary">No ingredients selected</div>';
    }
}

function generateRecipe() {
    document.getElementById('loading-recipe').style.display = 'block';
    document.getElementById('generate-btn').disabled = true;

    const recipeData = {
        types: selectedTypes,
        ingredients: selectedIngredients.map(i => i.name)
    };

    Android.generateRecipe(JSON.stringify(recipeData));
}

function displayGeneratedRecipe(recipeContent) {
    console.log("Displaying generated recipe");
    document.getElementById('loading-recipe').style.display = 'none';
    document.getElementById('recipe-result').innerHTML = recipeContent;
    document.getElementById('recipe-result-container').style.display = 'block';
    document.getElementById('generate-btn').disabled = false;
}

function saveRecipe() {
    const recipeContent = document.getElementById('recipe-result').innerHTML;
    const recipeTypes = selectedTypes.join(', ');
    let recipeTitle = recipeContent.split('\n')[0];
    if (recipeTitle.length > 50) {
        recipeTitle = recipeTitle.substring(0, 47) + '...';
    }

    Android.saveRecipe(recipeTitle, recipeContent);
}

function goBack() {
    window.location.href = "home.html";
}

document.getElementById('custom-type').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        addCustomType();
    }
});