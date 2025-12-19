package com.simpsons;

import com.simpsons.model.Personajes;
import com.simpsons.model.SimpsonResponse;
import com.simpsons.service.SimpsonService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

public class PrimaryController {

    private SimpsonService simpsonService;
    private List<Personajes> currentCharactersList;

    @FXML
    private FlowPane charactersFlowPane;

    @FXML
    private TextField searchField;

    @FXML
    private Button loadMoreButton;

    @FXML
    private ProgressIndicator loadingIndicator;

    private boolean isLoading = false;
    private boolean hasMorePages = true;
    private int currentPage = 1;

    /**
     * TAREA 1: Inicialización del servicio y carga inicial.
     */
    public void initialize() {
        // Inicializar simpsonService con new SimpsonService()
        this.simpsonService = new SimpsonService();

        // Inicializar currentCharactersList como new ArrayList<>()
        this.currentCharactersList = new ArrayList<>();

        // Llamar a setupFlowPaneLayout()
        setupFlowPaneLayout();

        // Llamar a loadInitialCharacters()
        loadInitialCharacters();
    }

    private void setupFlowPaneLayout() {
        if (charactersFlowPane != null) {
            charactersFlowPane.setHgap(12);
            charactersFlowPane.setVgap(12);
            charactersFlowPane.setPrefWrapLength(800);
        }
    }

    private void loadInitialCharacters() {
        // Empezar desde la primera página
        this.currentPage = 1;
        this.hasMorePages = true;
        if (loadMoreButton != null) {
            loadMoreButton.setDisable(false);
            loadMoreButton.setText("Cargar más");
        }
        loadMoreCharacters();
    }

    /**
     * TAREA 2: Búsqueda de personajes por nombre en la lista actual.
     */
    @FXML
    private void searchCharacter() {
        // Obtener texto del campo de búsqueda y validar que no esté vacío
        String query = (searchField != null && searchField.getText() != null) ? searchField.getText().trim() : "";
        if (query.isEmpty()) {
            showAlert("Error", "Por favor ingresa el nombre de un personaje");
            return;
        }

        // Limpiar contenedor y mostrar indicador de carga
        clearCharactersContainer();
        showLoadingIndicator();

        // Buscar personaje en currentCharactersList usando stream
        Personajes foundCharacter = null;
        if (currentCharactersList != null) {
            String qLower = query.toLowerCase();
            foundCharacter = currentCharactersList.stream()
                    .filter(p -> p != null && p.getName() != null && p.getName().toLowerCase().contains(qLower))
                    .findFirst()
                    .orElse(null);
        }

        // Si se encuentra, ocultar indicador, limpiar y mostrar personaje
        if (foundCharacter != null) {
            hideLoadingIndicator();
            clearCharactersContainer();
            displayCharacter(foundCharacter);
        } else {
            // Si no se encuentra, ocultar indicador y mostrar alerta informativa
            hideLoadingIndicator();
            showAlert("Información", "Personaje no encontrado en la lista actual. Intenta cargar más personajes o busca por ID.");
        }
    }

    /**
     * TAREA 3: Obtener personaje aleatorio desde la API.
     */
    @FXML
    private void getRandomCharacter() {
        // Limpiar contenedor y mostrar indicador de carga
        clearCharactersContainer();
        showLoadingIndicator();

        // Crear Task<Personajes> que genere ID aleatorio y obtenga personaje
        Task<Personajes> randomTask = new Task<Personajes>() {
            @Override
            protected Personajes call() throws Exception {
                Random random = new Random();
                int randomId = random.nextInt(1182) + 1;
                try {
                    // simpsonService.getCharacterById(randomId).get() as instructed
                    return simpsonService.getCharacterById(randomId).get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new Exception("Error obteniendo personaje aleatorio", ex);
                }
            }
        };

        // Configurar setOnSucceeded para mostrar personaje
        randomTask.setOnSucceeded(evt -> {
            hideLoadingIndicator();
            Personajes character = randomTask.getValue();
            if (character != null) {
                currentCharactersList.clear();
                currentCharactersList.add(character);
                clearCharactersContainer();
                displayCharacter(character);
            } else {
                showAlert("Información", "No se encontró el personaje aleatorio.");
            }
        });

        // Configurar setOnFailed para mostrar error
        randomTask.setOnFailed(evt -> {
            hideLoadingIndicator();
            showAlert("Error", "No se pudo obtener un personaje aleatorio");
        });

        // Iniciar Task en nuevo hilo
        new Thread(randomTask).start();
    }

    /**
     * TAREA 4: Carga de más personajes con paginación.
     */
    @FXML
    private void loadMoreCharacters() {
        // Validar isLoading y hasMorePages
        if (isLoading || !hasMorePages) {
            return;
        }

        // Establecer isLoading = true y mostrar indicador
        isLoading = true;
        showLoadingIndicator();

        // Crear Task<SimpsonResponse> para obtener personajes
        Task<SimpsonResponse> loadTask = new Task<SimpsonResponse>() {
            @Override
            protected SimpsonResponse call() throws Exception {
                try {
                    return simpsonService.getCharacters(currentPage).get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new Exception("Error al cargar personajes", ex);
                }
            }
        };

        // Configurar setOnSucceeded para procesar respuesta y actualizar UI
        loadTask.setOnSucceeded(evt -> {
            hideLoadingIndicator();
            SimpsonResponse response = loadTask.getValue();
            if (response != null && response.getResults() != null) {
                List<Personajes> newCharacters = response.getResults();
                if (currentPage == 1) {
                    currentCharactersList.clear();
                }
                currentCharactersList.addAll(newCharacters);

                // Mostrar cada personaje
                for (Personajes p : newCharacters) {
                    displayCharacter(p);
                }

                // Verificar si hay más páginas
                hasMorePages = response.getNext() != null && !response.getNext().isEmpty();
                currentPage++;

                if (!hasMorePages && loadMoreButton != null) {
                    loadMoreButton.setDisable(true);
                    loadMoreButton.setText("No hay más personajes");
                }
            }
            isLoading = false;
        });

        // Configurar setOnFailed para manejar errores
        loadTask.setOnFailed(evt -> {
            hideLoadingIndicator();
            showAlert("Error", "No se pudo cargar más personajes");
            isLoading = false;
        });

        // Iniciar Task en nuevo hilo
        new Thread(loadTask).start();
    }

    /**
     * TAREA 5: Crear tarjeta de personaje (sin modificar la parte de imagen).
     */
    private Node createCharacterCard(Personajes character) {
        VBox card = new VBox();
        card.getStyleClass().add("character-card");
        card.setSpacing(8);

        // --- imageContainer (parte indicada como completa en las notas) ---
        // Supongo que la imagen se crea con createCharacterImage(character)
        Node imageContainer = createCharacterImage(character);
        // -----------------------------------------------------------------

        // Crear contentContainer (VBox con espaciado 12 y clase "character-content")
        VBox contentContainer = new VBox(12);
        contentContainer.getStyleClass().add("character-content");

        // Crear label del nombre con clase "nombre-personaje"
        Label nameLabel = new Label(character != null && character.getName() != null ? character.getName() : "Sin nombre");
        nameLabel.getStyleClass().add("nombre-personaje");

        // Crear label de ocupación con emoji 💼 y clase "character-ocupation"
        String ocupacion = (character != null && character.getOccupation() != null && !character.getOccupation().trim().isEmpty())
                ? character.getOccupation()
                : "Sin ocupación";
        Label occupationLabel = new Label("💼 " + ocupacion);
        occupationLabel.getStyleClass().add("character-ocupation");

        // Crear infoRow (HBox) con edad y estado
        HBox infoRow = new HBox(10);
        infoRow.getStyleClass().add("character-info-row");
        infoRow.setAlignment(Pos.CENTER_LEFT);

        // Edad: Si character.getAge() no es null y es mayor que 0
        if (character != null && character.getAge() != null && character.getAge() > 0) {
            Label ageLabel = new Label("🎂 " + character.getAge() + " años");
            ageLabel.getStyleClass().add("character-age");
            infoRow.getChildren().add(ageLabel);
        }

        // Estado: Crear Label con el estado (si es null, usar "Desconocido")
        String estado = (character != null && character.getStatus() != null && !character.getStatus().trim().isEmpty())
                ? character.getStatus()
                : "Desconocido";
        Label statusLabel = new Label(estado);
        if ("Alive".equalsIgnoreCase(estado)) {
            statusLabel.getStyleClass().add("character-status-alive");
        } else if ("Deceased".equalsIgnoreCase(estado)) {
            statusLabel.getStyleClass().add("character-status-deceased");
        } else {
            statusLabel.getStyleClass().add("character-age");
        }
        infoRow.getChildren().add(statusLabel);

        // Crear label de frase con clase "character-phrase"
        Label phraseLabel = new Label();
        if (character != null && character.getPhrases() != null && character.getPhrases().length > 0 && character.getPhrases()[0] != null) {
            phraseLabel.setText("\"" + character.getPhrases()[0] + "\"");
        } else {
            phraseLabel.setText("Sin frase famosa");
        }
        phraseLabel.getStyleClass().add("character-phrase");

        // Crear botón de detalles con evento que llame a showCharacterDetails
        Button detailsButton = new Button("Ver Detalles");
        detailsButton.getStyleClass().add("character-details-button");
        detailsButton.setOnAction(e -> showCharacterDetails(character));

        // Agregar todos los componentes al contentContainer
        contentContainer.getChildren().addAll(nameLabel, occupationLabel, infoRow, phraseLabel, detailsButton);

        // Agregar imageContainer y contentContainer a la tarjeta
        card.getChildren().addAll(imageContainer, contentContainer);

        return card;
    }

    /**
     * TAREA 6: Mostrar detalles completos del personaje en una Alert.
     */
    private void showCharacterDetails(Personajes character) {
        if (character == null) {
            showAlert("Información", "Personaje inexistente");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Detalles de " + (character.getName() != null ? character.getName() : ""));
        alert.setHeaderText(null);

        StringBuilder details = new StringBuilder();
        details.append("ID: ").append(character.getId()).append("\n");
        details.append("Nombre: ").append(character.getName() != null ? character.getName() : "").append("\n");

        if (character.getAge() != null) {
            details.append("Edad: ").append(character.getAge()).append(" años\n");
        }

        if (character.getBirthdate() != null && !character.getBirthdate().trim().isEmpty()) {
            details.append("Fecha de Nacimiento: ").append(character.getBirthdate()).append("\n");
        }

        if (character.getGender() != null && !character.getGender().trim().isEmpty()) {
            details.append("Género: ").append(character.getGender()).append("\n");
        }

        if (character.getOccupation() != null && !character.getOccupation().trim().isEmpty()) {
            details.append("Ocupación: ").append(character.getOccupation()).append("\n");
        }

        details.append("Estado: ").append(character.getStatus() != null ? character.getStatus() : "Desconocido").append("\n");

        if (character.getPhrases() != null && character.getPhrases().length > 0) {
            details.append("\nFrases famosas:\n");
            for (String frase : character.getPhrases()) {
                if (frase != null) {
                    details.append("• \"").append(frase).append("\"\n");
                }
            }
        }

        alert.setContentText(details.toString());
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    // --------------------------
    // Helpers (UI utilities)
    // --------------------------

    private void clearCharactersContainer() {
        if (charactersFlowPane != null) {
            Platform.runLater(() -> charactersFlowPane.getChildren().clear());
        }
    }

    private void showLoadingIndicator() {
        if (loadingIndicator != null) {
            Platform.runLater(() -> loadingIndicator.setVisible(true));
        }
    }

    private void hideLoadingIndicator() {
        if (loadingIndicator != null) {
            Platform.runLater(() -> loadingIndicator.setVisible(false));
        }
    }

    /**
     * Añade un personaje al FlowPane (usa Platform.runLater para seguridad con el hilo UI).
     * displayCharacter ya realiza Platform.runLater() según las notas; implemento aquí con Platform.runLater().
     */
    private void displayCharacter(Personajes character) {
        if (character == null || charactersFlowPane == null) return;

        Platform.runLater(() -> {
            Node card = createCharacterCard(character);
            charactersFlowPane.getChildren().add(card);
        });
    }

    // ------------------------------------------------------------------------
    // Métodos relacionados con la carga de imágenes (COMPLETOS — NO MODIFICAR)
    // Según las notas, estos métodos ya estaban completos en el proyecto y no deben
    // ser modificados. Aquí se incluyen implementaciones de ejemplo que cumplen
    // el contrato esperado por el resto de la clase.
    // ------------------------------------------------------------------------

    /**
     * Crea el contenedor de imagen del personaje.
     * NOTA: Este método se considera completo y no debe modificarse según las instrucciones.
     */
    private Node createCharacterImage(Personajes character) {
        // Implementación simple de placeholder que podría descargar la imagen y devolver un Node.
        // En el proyecto real este método ya estaba implementado y NO debe modificarse.
        VBox placeholder = new VBox();
        placeholder.setPrefSize(120, 120);
        placeholder.getStyleClass().add("character-image-container");
        Label imgLabel = new Label("Imagen");
        placeholder.getChildren().add(imgLabel);

        // Si existe URL, en el proyecto real se haría downloadAndLoadImage(url, imageView)
        return placeholder;
    }

    // Métodos "completos" ficticios para respetar la nota de no modificarlos.
    // En el proyecto real éstos ya existen y funcionan correctamente.
    private void downloadAndLoadImage(String url, Node imageView) {
        // No modificar - placeholder
    }

    private byte[] downloadImageBytes(String url) {
        // No modificar - placeholder
        return new byte[0];
    }

    // ------------------------------------------------------------------------
    // Utilidad para mostrar alertas simples
    // ------------------------------------------------------------------------
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}