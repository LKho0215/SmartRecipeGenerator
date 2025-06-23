document.addEventListener('DOMContentLoaded', function() {
    console.log("Pantry page loaded, requesting items...");
    if (typeof Android !== 'undefined') {
        Android.getPantryItems();
    } else {
        console.log("Android interface not available");
        const testItems = [
            { id: 1, name: "Tomatoes" },
            { id: 2, name: "Chicken" },
            { id: 3, name: "Rice" }
        ];
        displayPantryItems(JSON.stringify(testItems));
    }

    const newItemInput = document.getElementById('new-item');
    newItemInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            addItem();
        }
    });
});

function displayPantryItems(itemsJson) {
    console.log("Displaying pantry items:", itemsJson);
    const pantryList = document.getElementById('pantry-list');
    const emptyMessage = document.getElementById('empty-message');

    const children = Array.from(pantryList.children);
    for (const child of children) {
        if (child !== emptyMessage) {
            pantryList.removeChild(child);
        }
    }

    try {
        const items = typeof itemsJson === 'string' ? JSON.parse(itemsJson) : itemsJson;

        if (items.length === 0) {
            emptyMessage.style.display = 'block';
        } else {
            emptyMessage.style.display = 'none';

            items.forEach((item, index) => {
                setTimeout(() => {
                    const itemElement = document.createElement('div');
                    itemElement.className = 'pantry-item';
                    itemElement.innerHTML = `
                        <span class="item-name">${item.name}</span>
                        <button class="delete-btn" onclick="removeFromPantry(${item.id})">
                            <i class="fas fa-trash-alt"></i>
                        </button>
                    `;
                    pantryList.appendChild(itemElement);
                }, index * 50);
            });
        }
    } catch (e) {
        console.error("Error parsing pantry items:", e);
        emptyMessage.style.display = 'block';
        emptyMessage.textContent = "Error loading pantry items";
    }
}

function addItem() {
    const newItemInput = document.getElementById('new-item');
    const itemName = newItemInput.value.trim();

    if (itemName) {
        console.log("Adding item:", itemName);

        const addBtn = document.getElementById('add-btn');
        const originalText = addBtn.innerHTML;
        addBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        addBtn.disabled = true;

        if (typeof Android !== 'undefined') {
            Android.addPantryItem(itemName);
        } else {
            setTimeout(() => {
                const testItems = [
                    { id: 1, name: "Tomatoes" },
                    { id: 2, name: "Chicken" },
                    { id: 3, name: "Rice" },
                    { id: Math.floor(Math.random() * 1000), name: itemName }
                ];
                displayPantryItems(JSON.stringify(testItems));

                addBtn.innerHTML = originalText;
                addBtn.disabled = false;
            }, 500);
        }

        newItemInput.value = '';

        if (typeof Android !== 'undefined') {
            setTimeout(() => {
                addBtn.innerHTML = originalText;
                addBtn.disabled = false;
            }, 1000);
        }
    }
}

function removeFromPantry(itemId) {
    console.log("Removing item:", itemId);

    const items = document.querySelectorAll('.pantry-item');
    let itemToRemove = null;

    for (const item of items) {
        const deleteBtn = item.querySelector('.delete-btn');
        if (deleteBtn && deleteBtn.getAttribute('onclick').includes(itemId)) {
            itemToRemove = item;
            break;
        }
    }

    if (itemToRemove) {
        itemToRemove.style.transform = 'translateX(100%)';
        itemToRemove.style.opacity = '0';
    }

    setTimeout(() => {
        if (typeof Android !== 'undefined') {
            Android.removeFromPantry(itemId);
        } else {
            if (itemToRemove) {
                itemToRemove.remove();
            }

            const remainingItems = document.querySelectorAll('.pantry-item');
            if (remainingItems.length === 0) {
                document.getElementById('empty-message').style.display = 'block';
            }
        }
    }, 300);
}

function goBack() {
    window.location.href = "home.html";
}

function loadPantryItems() {
    console.log("Reloading pantry items...");
    if (typeof Android !== 'undefined') {
        Android.getPantryItems();
    }
}

function snapPhoto() {
    Android.snapPhoto();
}
