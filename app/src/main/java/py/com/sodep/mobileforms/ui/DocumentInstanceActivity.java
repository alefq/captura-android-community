package py.com.sodep.mobileforms.ui;

import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;


import io.github.jokoframework.chake.R;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mobileforms.dataservices.DocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.FormsDAO;
import py.com.sodep.mobileforms.dataservices.documents.Document;
import py.com.sodep.mobileforms.dataservices.documents.DocumentMetadata;
import py.com.sodep.mobileforms.dataservices.sql.SQLDocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.sql.SQLFormsDAO;
import py.com.sodep.mobileforms.ui.rendering.UIDocumentInstanceRenderer;
import py.com.sodep.mobileforms.ui.rendering.exceptions.ParseException;
import py.com.sodep.mobileforms.ui.rendering.exceptions.RenderException;

/**
 * Displays the elements and values of a document that is not in draft.
 * <p>
 * Uses {@link py.com.sodep.mobileforms.ui.rendering.UIDocumentInstanceRenderer} to
 * render the document saved instance on the screen.
 * </p>
 * See CAP-96.
 * <p>
 * Created by rodrigo on 06/03/15.
 */
public class DocumentInstanceActivity extends AppCompatActivity {

    private static final String LOG_TAG = DocumentInstanceActivity.class.getSimpleName();

    public static final String EXTRA_DOCUMENT_METADATA = "metadata";

    private UIDocumentInstanceRenderer renderer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.document_instance);
        setTheme(R.style.FormTheme);

        try {
            DocumentMetadata documentMetadata = (DocumentMetadata) getIntent().getSerializableExtra(
                    EXTRA_DOCUMENT_METADATA);
            if (documentMetadata == null) {
                // this should not ever happen
                throw new IllegalStateException("No document metadata to display");
            } else {
                DocumentsDataSource documentsDAO = new SQLDocumentsDataSource();
                Document document = documentsDAO.getDocument(documentMetadata.getId());
                if (document != null) {
                    Form form = document.getForm();
                    if (form.getDefinition() == null) {
                        FormsDAO dao = new SQLFormsDAO();
                        form.setDefinition(dao.loadDefinition(form));
                    }
                    renderer = new UIDocumentInstanceRenderer(this, document);
                    renderer.render();
                } else {
                    finish();
                }
            }
        } catch (ParseException e) {
            DialogFragment dialog = new RenderErrorDialogFragment();
            Log.e(LOG_TAG, e.getMessage(), e);
            dialog.show(getSupportFragmentManager(), "ParseException");

        } catch (IllegalStateException e) {
            DialogFragment dialog = new RenderErrorDialogFragment();
            Log.e(LOG_TAG, e.getMessage(), e);
            dialog.show(getSupportFragmentManager(), "IllegalStateException");

        } catch (RenderException e) {
            DialogFragment dialog = new RenderErrorDialogFragment();
            Log.e(LOG_TAG, e.getMessage(), e);
            dialog.show(getSupportFragmentManager(), "RenderException");

        }

    }


}
