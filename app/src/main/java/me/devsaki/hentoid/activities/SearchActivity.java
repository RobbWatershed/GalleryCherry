package me.devsaki.hentoid.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.adapters.SelectedAttributeAdapter;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.fragments.SearchBottomSheetFragment;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.viewmodels.SearchViewModel;
import timber.log.Timber;

import static java.lang.String.format;

/**
 * Created by Robb on 2018/11
 */
public class SearchActivity extends BaseActivity {

    private TextView tagCategoryText;
    private TextView modelCategoryText;
    private TextView sourceCategoryText;


    // Book search button at the bottom of screen
    private TextView searchButton;
    // Caption that says "Select a filter" on top of screen
    private View startCaption;
    // Container where selected attributed are displayed
    private SelectedAttributeAdapter selectedAttributeAdapter;
    private RecyclerView searchTags;

    // ViewModel of this activity
    private SearchViewModel viewModel;


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder();
        builder.setUri(SearchActivityBundle.Builder.buildSearchUri(viewModel.getSelectedAttributesData().getValue()));
        outState.putAll(builder.getBundle());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        Uri searchUri = new SearchActivityBundle.Parser(savedInstanceState).getUri();
        if (searchUri != null) {
            List<Attribute> preSelectedAttributes = SearchActivityBundle.Parser.parseSearchUri(searchUri);
            if (preSelectedAttributes != null)
                viewModel.setSelectedAttributes(preSelectedAttributes);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        List<Attribute> preSelectedAttributes = null;
        if (intent != null && intent.getExtras() != null) {

            SearchActivityBundle.Parser parser = new SearchActivityBundle.Parser(intent.getExtras());
            Uri searchUri = parser.getUri();
            if (searchUri != null)
                preSelectedAttributes = SearchActivityBundle.Parser.parseSearchUri(searchUri);
        }

        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        startCaption = findViewById(R.id.startCaption);

        // Category buttons
        TextView anyCategoryText = findViewById(R.id.textCategoryAny);
        anyCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.TAG, AttributeType.ARTIST,
                AttributeType.CIRCLE, AttributeType.SERIE, AttributeType.CHARACTER, AttributeType.LANGUAGE)); // Everything but source !
        anyCategoryText.setEnabled(true);

        tagCategoryText = findViewById(R.id.textCategoryTag);
        tagCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.TAG));

        modelCategoryText = findViewById(R.id.textCategoryModel);
        modelCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.MODEL));

        sourceCategoryText = findViewById(R.id.textCategorySource);
        sourceCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.SOURCE));

        searchTags = findViewById(R.id.search_tags);
        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        searchTags.setLayoutManager(llm);
        selectedAttributeAdapter = new SelectedAttributeAdapter();
        selectedAttributeAdapter.setOnClickListener(this::onAttributeChosen);
        selectedAttributeAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() { // Auto-Scroll to last added item
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                llm.smoothScrollToPosition(searchTags, null, selectedAttributeAdapter.getItemCount());
            }
        });
        searchTags.setAdapter(selectedAttributeAdapter);

        searchButton = findViewById(R.id.search_fab);
        searchButton.setOnClickListener(v -> validateForm());

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        viewModel.getAttributesCountData().observe(this, this::onQueryUpdated);
        viewModel.getSelectedAttributesData().observe(this, this::onSelectedAttributesChanged);
        viewModel.getSelectedContentCount().observe(this, this::onBooksCounted);
        if (preSelectedAttributes != null) viewModel.setSelectedAttributes(preSelectedAttributes);
        else viewModel.emptyStart();
    }

    private void onQueryUpdated(SparseIntArray attrCount) {
        updateCategoryButton(tagCategoryText, attrCount, AttributeType.TAG);
        updateCategoryButton(modelCategoryText, attrCount, AttributeType.MODEL);
        updateCategoryButton(sourceCategoryText, attrCount, AttributeType.SOURCE);
    }

    private void updateCategoryButton(TextView button, SparseIntArray attrCount, AttributeType... types) {
        int count = 0;
        for (AttributeType type : types) count += attrCount.get(type.getCode(), 0);

        button.setText(format("%s (%s)", Helper.capitalizeString(types[0].getDisplayName()), count));
        button.setEnabled(count > 0);
    }


    private void onAttrButtonClick(AttributeType... attributeTypes) {
        SearchBottomSheetFragment.show(this, getSupportFragmentManager(), attributeTypes);
    }

    /**
     * @param attributes list of currently selected attributes
     */
    private void onSelectedAttributesChanged(List<Attribute> attributes) {
        if (attributes.isEmpty()) {
            searchTags.setVisibility(View.GONE);
            startCaption.setVisibility(View.VISIBLE);
        } else {
            searchTags.setVisibility(View.VISIBLE);
            startCaption.setVisibility(View.GONE);

            selectedAttributeAdapter.submitList(attributes);
        }
    }

    private void onAttributeChosen(View button) {
        Attribute a = (Attribute) button.getTag();
        if (a != null) viewModel.onAttributeUnselected(a);
    }

    private void onBooksCounted(int count) {
        if (count > 0) {
            searchButton.setText(getString(R.string.search_button).replace("%1", count + "").replace("%2", 1 == count ? "" : "s"));
            searchButton.setVisibility(View.VISIBLE);
        } else {
            searchButton.setVisibility(View.GONE);
        }
    }

    private void validateForm() {
        Uri searchUri = SearchActivityBundle.Builder.buildSearchUri(viewModel.getSelectedAttributesData().getValue());
        Timber.d("URI :%s", searchUri);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder().setUri(searchUri);
        Intent returnIntent = new Intent();
        returnIntent.putExtras(builder.getBundle());

        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }
}
