UPDATE wf_menu
SET visible = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'menu_workflow_designer'
  AND visible = TRUE;
