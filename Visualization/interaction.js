let mouse = {
    x: 0,
    y: 0,
    isInCanvas: function () {
        return !(this.x < 0 || this.x > canvas.width || this.y < 0 || this.y > canvas.height);
    }
}

document.addEventListener('mousemove', function (m) {
    mouse.x = m.x - canvas.getBoundingClientRect().left;
    mouse.y = m.y - canvas.getBoundingClientRect().top;
});
document.addEventListener('contextmenu', function (event) {
    if (mouse.isInCanvas()) {
        event.preventDefault();
    }
});

document.querySelectorAll('.dropdownHeader').forEach(element => {
    element.addEventListener('click', function () {
        const header = event.currentTarget;
        const items = header.nextElementSibling;
        const icon = header.querySelector(".icon");
        const checkboxLabel = header.querySelector(".check");

        if (checkboxLabel) {
            const checkbox = checkboxLabel.previousElementSibling;

            if (checkbox.checked) {
                items.style.display = "flex";
                icon.style.transform = "rotate(90deg)";
            }
            else {
                items.style.display = "none";
                icon.style.transform = "rotate(0deg)";
            }
        }
        else {
            if (items.style.display == "flex") {
                items.style.display = "none";
                icon.style.transform = "rotate(0deg)";
            }
            else {
                items.style.display = "flex";
                icon.style.transform = "rotate(90deg)";
            }
        }
    });
});


document.querySelectorAll('li').forEach(element => {
    element.addEventListener('click', function () {
        const listOption = event.currentTarget;
        const list = listOption.parentElement;
        const listItems = list.querySelectorAll('li');

        listItems.forEach(li => li.style.color = "#bbb");
        listOption.style.color = "#4e9abc";

        if (listOption.id == "stackedEntities") {
            VisualizationSettings.entityRender = "stacked";
        }
        else if (listOption.id == "transparentEntities") {
            VisualizationSettings.entityRender = "transparent";
        }

        if (listOption.id == "textHighlighted") {
            VisualizationSettings.textHighlight = "text";
        }
        else if (listOption.id == "backgroundHighlighted") {
            VisualizationSettings.textHighlight = "background";
        }
        else if (listOption.id == "nothingHighlighted") {
            VisualizationSettings.textHighlight = "none";
        }
    });
});

document.querySelectorAll('.slider').forEach(slider => {
    const input = slider.querySelector('input[type="range"]');
    const valueSpan = slider.querySelector('.sliderValue');

    valueSpan.textContent = input.value;

    input.addEventListener('input', () => {
        valueSpan.textContent = input.value;

        if(input.id == "outlineWeight") {
            VisualizationSettings.outlineWeight = Number(input.value);
        }
        else if (input.id == "cornerRadius") {
            VisualizationSettings.cornerRadius = Number(input.value);
        }
    });
});

document.getElementById('outlineColorInput').addEventListener('input', (e) => {
    VisualizationSettings.outlineColor = e.target.value;
});

