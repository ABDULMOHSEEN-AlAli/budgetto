package pl.cyfrogen.budget.ui.main.home;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pl.cyfrogen.budget.firebase.FirebaseElement;
import pl.cyfrogen.budget.firebase.FirebaseObserver;
import pl.cyfrogen.budget.firebase.models.UserSettings;
import pl.cyfrogen.budget.base.BaseFragment;
import pl.cyfrogen.budget.models.Category;
import pl.cyfrogen.budget.util.CurrencyHelper;
import pl.cyfrogen.budget.models.DefaultCategories;
import pl.cyfrogen.budget.R;
import pl.cyfrogen.budget.firebase.ListDataSet;
import pl.cyfrogen.budget.firebase.viewmodel_factories.UserProfileViewModelFactory;
import pl.cyfrogen.budget.firebase.viewmodel_factories.TopWalletEntriesViewModelFactory;
import pl.cyfrogen.budget.firebase.models.User;
import pl.cyfrogen.budget.libraries.Gauge;
import pl.cyfrogen.budget.firebase.models.WalletEntry;

public class HomeFragment extends BaseFragment {
    private User userData;
    private ListDataSet<WalletEntry> walletEntryListDataSet;

    public static final CharSequence TITLE = "Home";
    private ListView favoriteListView;
    private Gauge gauge;
    private TopCategoriesAdapter adapter;
    private ArrayList<TopCategoryListViewModel> categoryModelsHome;
    private TextView totalBalanceTextView;
    private TextView gaugeLeftBalanceTextView;
    private TextView gaugeLeftLine1TextView;
    private TextView gaugeLeftLine2TextView;
    private TextView gaugeRightBalanceTextView;
    private TextView gaugeRightLine1TextView;
    private TextView gaugeRightLine2TextView;
    private TextView gaugeBalanceLeftTextView;
    private boolean firstUserUpdated;

    public static HomeFragment newInstance() {

        return new HomeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);


    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        categoryModelsHome = new ArrayList<>();

        gauge = view.findViewById(R.id.gauge);
        gauge.setValue(50);

        totalBalanceTextView = view.findViewById(R.id.total_balance_textview);
        gaugeLeftBalanceTextView = view.findViewById(R.id.gauge_left_balance_text_view);
        gaugeLeftLine1TextView = view.findViewById(R.id.gauge_left_line1_textview);
        gaugeLeftLine2TextView = view.findViewById(R.id.gauge_left_line2_textview);
        gaugeRightBalanceTextView = view.findViewById(R.id.gauge_right_balance_text_view);
        gaugeRightLine1TextView = view.findViewById(R.id.gauge_right_line1_textview);
        gaugeRightLine2TextView = view.findViewById(R.id.gauge_right_line2_textview);
        gaugeBalanceLeftTextView = view.findViewById(R.id.left_balance_textview);


        favoriteListView = view.findViewById(R.id.favourite_categories_list_view);
        adapter = new TopCategoriesAdapter(categoryModelsHome, getActivity().getApplicationContext());
        favoriteListView.setAdapter(adapter);




        UserProfileViewModelFactory.getModel(getUid(), getActivity()).observe(this, new FirebaseObserver<FirebaseElement<User>>() {
            @Override
            public void onChanged(FirebaseElement<User> firebaseElement) {
                if (firebaseElement.hasNoError()) {
                    HomeFragment.this.userData = firebaseElement.getElement();
                    dataUpdated();

                    Calendar startDate = getStartDate(userData);
                    Calendar endDate = getEndDate(userData);

                    if(firstUserUpdated) {
                        TopWalletEntriesViewModelFactory.getModel(getUid(), startDate, endDate, getActivity()).setDateFilter(startDate, endDate);

                    } else {
                        firstUserUpdated = true;
                        TopWalletEntriesViewModelFactory.getModel(getUid(), startDate, endDate, getActivity()).observe(HomeFragment.this, new FirebaseObserver<FirebaseElement<ListDataSet<WalletEntry>>>() {

                            @Override
                            public void onChanged(FirebaseElement<ListDataSet<WalletEntry>> firebaseElement) {
                                if (firebaseElement.hasNoError()) {
                                    HomeFragment.this.walletEntryListDataSet = firebaseElement.getElement();
                                    dataUpdated();
                                }
                            }
                        });


                    }
                    firstUserUpdated = true;

                }
            }
        });


    }

    private void dataUpdated() {
        if (userData == null || walletEntryListDataSet == null) return;

        List<WalletEntry> entryList = new ArrayList<>(walletEntryListDataSet.getList());


        Calendar startDate = getStartDate(userData);
        Calendar endDate = getEndDate(userData);

        DateFormat dateFormat = new SimpleDateFormat("dd-MM");




        long expensesSumInDateRange = 0;
        long incomesSumInDateRange = 0;

        HashMap<Category, Long> categoryModels = new HashMap<>();
        for (WalletEntry walletEntry : entryList) {
            if (walletEntry.balanceDifference > 0) {
                incomesSumInDateRange += walletEntry.balanceDifference;
                continue;
            }
            expensesSumInDateRange += walletEntry.balanceDifference;
            Category category = DefaultCategories.searchCategory(walletEntry.categoryID);
            if (categoryModels.get(category) != null)
                categoryModels.put(category, categoryModels.get(category) + walletEntry.balanceDifference);
            else
                categoryModels.put(category, walletEntry.balanceDifference);

        }

        categoryModelsHome.clear();
        for (Map.Entry<Category, Long> categoryModel : categoryModels.entrySet()) {
            categoryModelsHome.add(new TopCategoryListViewModel(categoryModel.getKey(), categoryModel.getKey().getCategoryVisibleName(getContext()),
                    userData.currency, categoryModel.getValue()));
        }

        Collections.sort(categoryModelsHome, new Comparator<TopCategoryListViewModel>() {
            @Override
            public int compare(TopCategoryListViewModel o1, TopCategoryListViewModel o2) {
                return Long.compare(o1.getMoney(), o2.getMoney());
            }
        });


        adapter.notifyDataSetChanged();
        totalBalanceTextView.setText(CurrencyHelper.formatCurrency(userData.currency, userData.wallet.sum));

        if (userData.userSettings.homeCounterType == UserSettings.HOME_COUNTER_TYPE_SHOW_LIMIT) {
            gaugeLeftBalanceTextView.setText(CurrencyHelper.formatCurrency(userData.currency, 0));
            gaugeLeftLine1TextView.setText(dateFormat.format(startDate.getTime()));
            gaugeLeftLine2TextView.setVisibility(View.INVISIBLE);
            gaugeRightBalanceTextView.setText(CurrencyHelper.formatCurrency(userData.currency, userData.userSettings.limit));
            gaugeRightLine1TextView.setText(dateFormat.format(endDate.getTime()));
            gaugeRightLine2TextView.setVisibility(View.INVISIBLE);

            gauge.setPointStartColor(ContextCompat.getColor(getContext(), R.color.gauge_white));
            gauge.setPointEndColor(ContextCompat.getColor(getContext(), R.color.gauge_white));
            gauge.setStrokeColor(ContextCompat.getColor(getContext(), R.color.gauge_gray));

            long limit = userData.userSettings.limit;
            long expenses = -expensesSumInDateRange;
            int percentage = (int) (expenses * 100 /(double) limit);
            if(percentage > 100) percentage = 100;
            gauge.setValue(percentage);
            gaugeBalanceLeftTextView.setText(CurrencyHelper.formatCurrency(userData.currency, limit-expenses) + " left");


        } else {
            gaugeLeftBalanceTextView.setText(CurrencyHelper.formatCurrency(userData.currency, incomesSumInDateRange));
            gaugeLeftLine1TextView.setText("Incomes");
            gaugeLeftLine2TextView.setVisibility(View.INVISIBLE);
            gaugeRightBalanceTextView.setText(CurrencyHelper.formatCurrency(userData.currency, expensesSumInDateRange));
            gaugeRightLine1TextView.setText("Expenses");
            gaugeRightLine2TextView.setVisibility(View.INVISIBLE);

            gauge.setPointStartColor(ContextCompat.getColor(getContext(), R.color.gauge_income));
            gauge.setPointEndColor(ContextCompat.getColor(getContext(), R.color.gauge_income));
            gauge.setStrokeColor(ContextCompat.getColor(getContext(), R.color.gauge_expense));
            if (incomesSumInDateRange - expensesSumInDateRange != 0)
                gauge.setValue((int) (incomesSumInDateRange * 100 /(double) (incomesSumInDateRange - expensesSumInDateRange)));

            gaugeBalanceLeftTextView.setText(dateFormat.format(startDate.getTime()) + " - " +
                    dateFormat.format(endDate.getTime()));
        }
    }

    private Calendar getStartDate(User userData) {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(getUserFirstDayOfWeek(userData));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.clear(Calendar.MINUTE);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
        if(userData.userSettings.homeCounterPeriod == UserSettings.HOME_COUNTER_PERIOD_WEEKLY) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            if(new Date().getTime() < cal.getTime().getTime())
                cal.add(Calendar.DATE, -7);
        }
         else {
            cal.set(Calendar.DAY_OF_MONTH, userData.userSettings.dayOfMonthStart+1);
            if(new Date().getTime() < cal.getTime().getTime())
                cal.add(Calendar.MONTH, -1);
        }

        return cal;
    }


    private Calendar getEndDate(User userData) {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(getUserFirstDayOfWeek(userData));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.clear(Calendar.MILLISECOND);
        if(userData.userSettings.homeCounterPeriod == UserSettings.HOME_COUNTER_PERIOD_WEEKLY) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            if(new Date().getTime() < cal.getTime().getTime())
                cal.add(Calendar.DATE, -7);
            cal.add(Calendar.DATE, 6);
        }
        else {
            cal.set(Calendar.DAY_OF_MONTH, userData.userSettings.dayOfMonthStart+1);
            if(new Date().getTime() < cal.getTime().getTime())
                cal.add(Calendar.MONTH, -1);
            cal.add(Calendar.MONTH, 1);
            cal.add(Calendar.DATE, -1);
        }
        return cal;
    }

    private int getUserFirstDayOfWeek(User userData) {
        switch (userData.userSettings.dayOfWeekStart) {
            case 0 : return Calendar.MONDAY;
            case 1 : return Calendar.TUESDAY;
            case 2 : return Calendar.WEDNESDAY;
            case 3 : return Calendar.THURSDAY;
            case 4 : return Calendar.FRIDAY;
            case 5 : return Calendar.SATURDAY;
            case 6 : return Calendar.SUNDAY;
        }
        return 0;
    }
}